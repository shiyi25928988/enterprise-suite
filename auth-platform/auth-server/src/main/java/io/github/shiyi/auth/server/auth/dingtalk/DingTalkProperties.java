package io.github.shiyi.auth.server.auth.dingtalk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DingTalk (钉钉) scan-login configuration (M5c). Disabled by default:
 *
 * <pre>
 * auth:
 *   dingtalk:
 *     enabled: true
 *     app-key: dingxxxx
 *     app-secret: ${DINGTALK_SECRET}
 *     # redirect-uri: https://iam.example.com/dingtalk-callback
 * </pre>
 *
 * <p>{@code api-base-url} / {@code oapi-base-url} default to the public hosts
 * and exist so E2E tests can point the client at a stub.</p>
 */
@ConfigurationProperties(prefix = "auth.dingtalk")
public class DingTalkProperties {

    private boolean enabled = false;
    private String appKey;
    private String appSecret;
    /** Optional override of the QR-login redirect target; defaults to {origin}/dingtalk-callback. */
    private String redirectUri;
    private String apiBaseUrl = "https://api.dingtalk.com";
    private String oapiBaseUrl = "https://oapi.dingtalk.com";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    public String getOapiBaseUrl() { return oapiBaseUrl; }
    public void setOapiBaseUrl(String oapiBaseUrl) { this.oapiBaseUrl = oapiBaseUrl; }
}
