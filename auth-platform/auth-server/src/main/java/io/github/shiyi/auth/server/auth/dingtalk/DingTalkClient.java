package io.github.shiyi.auth.server.auth.dingtalk;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Minimal DingTalk API client for scan login (M5c), two hosts:
 *
 * <ol>
 *   <li>{@code api.dingtalk.com} (new OAuth2): the QR code lands on the
 *       redirect URI as {@code ?code=}; exchange it via
 *       {@code POST /v1.0/oauth2/userAccessToken}, then read the scanner's
 *       identity from {@code GET /v1.0/contact/users/me} — that endpoint
 *       yields the <em>unionId</em>, not the corp userid.</li>
 *   <li>{@code oapi.dingtalk.com} (legacy): {@code /topapi/user/getbyunionid}
 *       maps unionId → corp userid, which is what sync imported and what the
 *       platform binds on.</li>
 * </ol>
 *
 * <p>Plain {@link HttpClient} with an explicit {@code Content-Type:
 * application/json} — same transport choice as the M8 sync client; the
 * auto-configured RestClient proved unreliable for programmatic JSON bodies.</p>
 */
@Component
public class DingTalkClient {

    private static final Logger log = LoggerFactory.getLogger(DingTalkClient.class);

    private final DingTalkProperties props;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper json = new ObjectMapper();

    private volatile String corpToken;
    private volatile Instant corpTokenExpiry = Instant.EPOCH;

    public DingTalkClient(DingTalkProperties props) {
        this.props = props;
    }

    /** Exchanges a QR-login code for the scanner's corp userid. */
    public String useridFromCode(String code) {
        String accessToken = userAccessToken(code);
        String unionId = unionIdOfCurrentUser(accessToken);
        String userid = useridByUnionId(unionId);
        log.info("DingTalk code exchange OK: unionId {} -> userid {}", unionId, userid);
        return userid;
    }

    private String userAccessToken(String code) {
        JsonNode resp = postJson(props.getApiBaseUrl() + "/v1.0/oauth2/userAccessToken", Map.of(
                "clientId", props.getAppKey(),
                "clientSecret", props.getAppSecret(),
                "code", code,
                "grantType", "authorization_code"));
        String token = resp.path("accessToken").asText(null);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DingTalk userAccessToken exchange failed: " + resp);
        }
        return token;
    }

    private String unionIdOfCurrentUser(String accessToken) {
        JsonNode resp = getJson(props.getApiBaseUrl() + "/v1.0/contact/users/me",
                Map.of("x-acs-dingtalk-access-token", accessToken));
        String unionId = resp.path("unionId").asText(null);
        if (unionId == null || unionId.isBlank()) {
            throw new IllegalStateException("DingTalk users/me failed (invalid or expired code): " + resp);
        }
        return unionId;
    }

    private String useridByUnionId(String unionId) {
        JsonNode resp = postJson(
                props.getOapiBaseUrl() + "/topapi/user/getbyunionid?access_token=" + corpAccessToken(),
                Map.of("unionid", unionId));
        if (resp.path("errcode").asInt(-1) != 0) {
            throw new IllegalStateException("DingTalk getbyunionid failed for unionId "
                    + unionId + ": " + resp);
        }
        return resp.path("result").path("userid").asText(null);
    }

    private synchronized String corpAccessToken() {
        if (corpToken != null && Instant.now().isBefore(corpTokenExpiry)) {
            return corpToken;
        }
        JsonNode resp = getJson(props.getOapiBaseUrl() + "/gettoken?appkey="
                + props.getAppKey() + "&appsecret=" + props.getAppSecret(), Map.of());
        if (resp.path("errcode").asInt(-1) != 0) {
            throw new IllegalStateException("DingTalk gettoken failed: " + resp);
        }
        corpToken = resp.path("access_token").asText(null);
        if (corpToken == null || corpToken.isBlank()) {
            throw new IllegalStateException("DingTalk gettoken returned no access_token");
        }
        corpTokenExpiry = Instant.now().plusSeconds(7200 - 120);
        return corpToken;
    }

    // ---------- transport ----------

    private JsonNode postJson(String url, Map<String, Object> body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return json.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("DingTalk POST " + url + " failed: " + e.getMessage(), e);
        }
    }

    private JsonNode getJson(String url, Map<String, String> headers) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET();
            headers.forEach(b::header);
            HttpResponse<String> response = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return json.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("DingTalk GET " + url + " failed: " + e.getMessage(), e);
        }
    }
}
