package io.github.shiyi.auth.user.sync;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * WeCom (企业微信) directory provider.
 *
 * <p>Endpoints: gettoken / department/list / user/list on
 * {@code qyapi.weixin.qq.com}. The contact secret ("通讯录同步" secret) must be
 * used, not an app agent secret, or department/user scopes will be empty.
 * Base URL configurable for stub tests.</p>
 */
public class WeComDirectory implements DirectoryProvider {

    private final SyncProperties.Wecom props;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WeComDirectory(SyncProperties.Wecom props) {
        this.props = props;
    }

    @Override
    public String source() {
        return "wecom";
    }

    @Override
    public DirectorySnapshot fetch() throws Exception {
        String token = accessToken();
        JsonNode deptResp = getJson(props.getBaseUrl() + "/cgi-bin/department/list?access_token=" + token);
        requireOk(deptResp, "department/list");

        List<ExternalOrg> orgs = new ArrayList<>();
        for (JsonNode d : deptResp.path("department")) {
            String id = d.path("id").asText();
            orgs.add(new ExternalOrg(id, d.path("name").asText(null),
                    "1".equals(id) ? null : d.path("parentid").asText(null),
                    d.has("order") ? d.path("order").asInt() : null));
        }

        // user/list is per-department; fetch_child=1 from root covers everything,
        // but per-leaf calls dedupe more reliably across unusual trees
        List<ExternalUser> users = new ArrayList<>();
        Set<String> userSeen = new LinkedHashSet<>();
        for (ExternalOrg org : orgs) {
            JsonNode resp = getJson(props.getBaseUrl() + "/cgi-bin/user/list?access_token=" + token
                    + "&department_id=" + URLEncoder.encode(org.externalId(), StandardCharsets.UTF_8)
                    + "&fetch_child=0");
            requireOk(resp, "user/list");
            for (JsonNode u : resp.path("userlist")) {
                String userid = u.path("userid").asText(null);
                if (userid != null && userSeen.add(userid)) {
                    users.add(new ExternalUser(userid, u.path("name").asText(null),
                            u.hasNonNull("mobile") ? u.path("mobile").asText() : null,
                            u.hasNonNull("job_number") ? u.path("job_number").asText() : null,
                            u.path("department").path(0).asText(null)));
                }
            }
        }
        return new DirectorySnapshot(orgs, users);
    }

    private String accessToken() throws Exception {
        JsonNode resp = getJson(props.getBaseUrl() + "/cgi-bin/gettoken?corpid="
                + URLEncoder.encode(props.getCorpId(), StandardCharsets.UTF_8)
                + "&corpsecret=" + URLEncoder.encode(props.getCorpSecret(), StandardCharsets.UTF_8));
        String token = resp.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("wecom gettoken returned no access_token: " + resp);
        }
        return token;
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return json.readTree(response.body());
    }

    private void requireOk(JsonNode resp, String api) {
        if (resp.path("errcode").asInt(-1) != 0) {
            throw new IllegalStateException("wecom " + api + " failed: errcode="
                    + resp.path("errcode").asInt() + " " + resp.path("errmsg").asText());
        }
    }
}
