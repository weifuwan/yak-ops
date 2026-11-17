package io.yak.ops.agent.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.yak.ops.agent.config.AgentConfiguration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin OpenAI-compatible chat client built on RestTemplate (zero extra dependency).
 * Supports the tool-calling protocol used by the agent orchestration loop.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
public class LlmClient {

    private static final int MAX_TOKENS = 2048;

    private final AgentConfiguration configuration;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmClient(AgentConfiguration configuration) {
        this.configuration = configuration;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(Math.max(10, configuration.getLlmTimeoutSeconds()) * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    public ChatResult chat(List<Message> messages, List<Map<String, Object>> tools) {
        long startedAt = System.currentTimeMillis();
        String url = normalizeBaseUrl(configuration.getLlmBaseUrl()) + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", configuration.getLlmModel());
        body.put("messages", toPayload(messages));
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        body.put("temperature", 0.2);
        body.put("max_tokens", MAX_TOKENS);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(configuration.getLlmApiKey());

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("LLM call failed: " + ex.getMessage(), ex);
        }

        ChatResult result = parse(response.getBody());
        result.setLatencyMs(System.currentTimeMillis() - startedAt);
        return result;
    }

    private ChatResult parse(String raw) {
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse LLM response", ex);
        }

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new IllegalStateException("LLM response has no choices");
        }

        JsonNode messageNode = choices.get(0).path("message");
        JsonNode contentNode = messageNode.path("content");
        String content = contentNode.isMissingNode() || contentNode.isNull() ? null : contentNode.asText();

        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode callsNode = messageNode.path("tool_calls");
        if (callsNode.isArray()) {
            for (JsonNode call : callsNode) {
                toolCalls.add(ToolCall.builder()
                        .id(call.path("id").asText())
                        .name(call.path("function").path("name").asText())
                        .arguments(call.path("function").path("arguments").asText("{}"))
                        .build());
            }
        }

        JsonNode usage = root.path("usage");
        return ChatResult.builder()
                .content(content)
                .toolCalls(toolCalls)
                .promptTokens(usage.path("prompt_tokens").asInt(0))
                .completionTokens(usage.path("completion_tokens").asInt(0))
                .build();
    }

    private List<Map<String, Object>> toPayload(List<Message> messages) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Message message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContent() == null ? "" : message.getContent());
            if (message.getToolCallId() != null) {
                item.put("tool_call_id", message.getToolCallId());
            }
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                List<Map<String, Object>> calls = new ArrayList<>();
                for (ToolCall call : message.getToolCalls()) {
                    Map<String, Object> function = new LinkedHashMap<>();
                    function.put("name", call.getName());
                    function.put("arguments", call.getArguments());
                    Map<String, Object> callPayload = new LinkedHashMap<>();
                    callPayload.put("id", call.getId());
                    callPayload.put("type", "function");
                    callPayload.put("function", function);
                    calls.add(callPayload);
                }
                item.put("tool_calls", calls);
            }
            payload.add(item);
        }
        return payload;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @Data
    @Builder
    public static class Message {

        private String role;

        private String content;

        private String toolCallId;

        private List<ToolCall> toolCalls;

        public static Message system(String content) {
            return Message.builder().role("system").content(content).build();
        }

        public static Message user(String content) {
            return Message.builder().role("user").content(content).build();
        }

        public static Message assistant(String content, List<ToolCall> toolCalls) {
            return Message.builder().role("assistant").content(content).toolCalls(toolCalls).build();
        }

        public static Message tool(String toolCallId, String content) {
            return Message.builder().role("tool").content(content).toolCallId(toolCallId).build();
        }
    }

    @Data
    @Builder
    public static class ToolCall {

        private String id;

        private String name;

        private String arguments;
    }

    @Data
    @Builder
    public static class ChatResult {

        private String content;

        private List<ToolCall> toolCalls;

        private int promptTokens;

        private int completionTokens;

        private long latencyMs;
    }
}
