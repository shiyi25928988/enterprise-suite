package io.github.shiyi.auth.user.sync;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * DingTalk directory provider.
 *
 * <p>Endpoints (v2 topapi): token on {@code api.dingtalk.com}, dept/user
 * queries on {@code oapi.dingtalk.com}. Departments are walked top-down from
 * the root ({@code dept_id=1}) via listsub; users are listed per department
 * with cursor paging. Both base URLs are configurable so tests can point at a
 * stub server.</p>
 */
public class DingTalkDirectory implements DirectoryProvider {

    private final SyncProperties.Dingtalk props;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public DingTalkDirectory(SyncProperties.Dingtalk props) {
        this.props = props;
    }

    @Override
    public String source() {
        return "dingtalk";
    }

    @Override
    public DirectorySnapshot fetch() throws Exception {
        String token = accessToken();

        // walk departments breadth-first; the root (dept_id=1) is never returned
        // by listsub, so synthesize it — children then resolve their parent and
        // users mounted directly under the root are fetched as well
        List<ExternalOrg> orgs = new ArrayList<>();
        orgs.add(new ExternalOrg("1", props.getRootName(), null, 0));
        Set<String> seen = new LinkedHashSet<>();
        seen.add("1");
        List<String> frontier = new ArrayList<>(List.of("1"));
        while (!frontier.isEmpty()) {
            String parentId = frontier.removeFirst();
            ObjectNode body = json.createObjectNode()
                    .put("dept_id", Long.parseLong(parentId));
            JsonNode resp = postJson(props.getBaseUrl() + "/topapi/v2/department/listsub?access_token=" + token, body);
            requireOk(resp, "department/listsub");
            for (JsonNode d : resp.path("result")) {
                String id = d.path("dept_id").asText();
                if (seen.add(id)) {
                    orgs.add(new ExternalOrg(id, d.path("name").asText(null),
                            parentId,
                            d.has("order") ? d.path("order").asInt() : null));
                    frontier.add(id);
                }
            }
        }

        // users per department, cursor paged
        List<ExternalUser> users = new ArrayList<>();
        Set<String> userSeen = new LinkedHashSet<>();
        for (ExternalOrg org : orgs) {
            long cursor = 0;
            boolean hasMore = true;
            while (hasMore) {
                ObjectNode body = json.createObjectNode()
                        .put("dept_id", Long.parseLong(org.externalId()))
                        .put("cursor", cursor)
                        .put("size", 100);
                JsonNode resp = postJson(props.getBaseUrl() + "/topapi/v2/user/list?access_token=" + token, body);
                requireOk(resp, "user/list");
                for (JsonNode u : resp.path("result").path("list")) {
                    String userid = u.path("userid").asText(null);
                    if (userid != null && userSeen.add(userid)) {
                        users.add(new ExternalUser(userid, u.path("name").asText(null),
                                u.hasNonNull("mobile") ? u.path("mobile").asText() : null,
                                u.hasNonNull("job_number") ? u.path("job_number").asText() : null,
                                u.path("dept_id_list").path(0).asText(null)));
                    }
                }
                hasMore = resp.path("result").path("has_more").asBoolean(false);
                if (hasMore) {
                    cursor = resp.path("result").path("next_cursor").asLong(0);
                }
            }
        }
        return new DirectorySnapshot(orgs, users);
    }

    private String accessToken() throws Exception {
        ObjectNode body = json.createObjectNode()
                .put("appKey", props.getAppKey())
                .put("appSecret", props.getAppSecret());
        JsonNode resp = postJson(props.getTokenUrl(), body);
        String token = resp.path("accessToken").asText(null);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("dingtalk token endpoint returned no accessToken: " + resp);
        }
        return token;
    }

    private JsonNode postJson(String url, ObjectNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return json.readTree(response.body());
    }

    private void requireOk(JsonNode resp, String api) {
        if (resp.path("errcode").asInt(-1) != 0) {
            throw new IllegalStateException("dingtalk " + api + " failed: errcode="
                    + resp.path("errcode").asInt() + " " + resp.path("errmsg").asText());
        }
    }
}
