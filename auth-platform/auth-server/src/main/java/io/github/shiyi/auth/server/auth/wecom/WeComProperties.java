package io.github.shiyi.auth.server.auth.wecom;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WeCom (企业微信) scan-login configuration. Disabled by default; enable with:
 *
 * <pre>
 * auth:
 *   wecom:
 *     enabled: true
 *     corp-id: ww1234567890
 *     agent-id: 1000002
 *     secret: ${WECOM_SECRET}
 * </pre>
 */
@ConfigurationProperties(prefix = "auth.wecom")
public class WeComProperties {

    private boolean enabled = false;
    private String corpId;
    private String agentId;
    private String secret;
    /**
     * Optional override of the QR-login redirect target; defaults to
     * {origin}/wecom-callback served by the SPA.
     */
    private String redirectUri;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getCorpId() { return corpId; }
    public void setCorpId(String corpId) { this.corpId = corpId; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
}
