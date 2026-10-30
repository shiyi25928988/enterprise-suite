package io.github.shiyi.auth.workflow.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

/**
 * W1-4: the AI participant node. A Flowable service task
 * ({@code flowable:delegateExpression="${aiReviewDelegate}"}) placed between
 * start and the human approval: it reads the submitted form variables, asks
 * an OpenAI-compatible LLM for a structured recommendation, and drops
 * {@code aiSuggestion} ("approve" / "reject" / "unavailable") +
 * {@code aiReason} into process variables. The human approval task renders
 * them next to the form — the AI pre-reviews, the human decides.
 *
 * <p>Every failure path degrades to "unavailable": a dead LLM must never
 * wedge a process instance.</p>
 */
public class AiReviewDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(AiReviewDelegate.class);

    private final AiReviewProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public AiReviewDelegate(AiReviewProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void execute(DelegateExecution execution) {
        if (!props.isEnabled()) {
            set(execution, "unavailable", "AI pre-review is disabled (auth.flow.ai.enabled=false)");
            return;
        }
        try {
            Review review = callLlm(execution.getVariables());
            set(execution, review.suggestion(), review.reason());
        } catch (Exception e) {
            log.warn("AI pre-review failed for instance {}: {}",
                    execution.getProcessInstanceId(), e.toString());
            set(execution, "unavailable", "AI pre-review failed: " + e.getMessage());
        }
    }

    private void set(DelegateExecution execution, String suggestion, String reason) {
        execution.setVariable(WorkflowVar.AI_SUGGESTION, suggestion);
        execution.setVariable(WorkflowVar.AI_REASON, reason);
    }

    private record Review(String suggestion, String reason) {
    }

    private Review callLlm(Map<String, Object> variables) throws Exception {
        Map<String, Object> form = new LinkedHashMap<>();
        variables.forEach((k, v) -> {
            if (!WorkflowVar.RESERVED.contains(k)) {
                form.put(k, v);
            }
        });

        String system = """
                你是企业审批流的预审助手。根据申请内容给出结构化建议。
                只输出一个 JSON 对象，格式：{"suggestion":"approve"|"reject"|"escalate","reason":"一句话理由"}
                规则：金额/时长在常规范围内且事由合理 → approve；明显违规或信息可疑 → reject；超出你的判断范围 → escalate。
                """;
        String user = "申请内容：\n" + mapper.writeValueAsString(form);

        Map<String, Object> body = Map.of(
                "model", props.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                "temperature", 0);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl().replaceAll("/$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            throw new IllegalStateException("LLM HTTP " + resp.statusCode());
        }
        return parse(mapper.readTree(resp.body()));
    }

    private Review parse(tools.jackson.databind.JsonNode root) {
        String content = root.path("choices").path(0).path("message").path("content").asString("");
        // tolerate markdown fences around the JSON
        String json = content.replaceAll("(?s)^```(json)?|```$", "").trim();
        tools.jackson.databind.JsonNode o = mapper.readTree(json);
        String suggestion = switch (o.path("suggestion").asString("")) {
            case "approve" -> "approve";
            case "reject" -> "reject";
            default -> "escalate";
        };
        return new Review(suggestion, o.path("reason").asString(""));
    }
}
