package io.github.shiyi.auth.workflow.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * auth.flow.ai.* — LLM pre-review for approval nodes. OpenAI-compatible
 * chat-completions endpoint so any self-hosted gateway (vLLM, one-api, …)
 * or public provider works. Off by default: the AI delegate then marks its
 * review "unavailable" and the human task proceeds as usual.
 */
@ConfigurationProperties("auth.flow.ai")
public class AiReviewProperties {

    private boolean enabled;
    /** e.g. https://api.example.com/v1 (…/chat/completions is appended). */
    private String baseUrl;
    private String apiKey;
    private String model;
    private int timeoutSeconds = 30;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
