package io.yak.ops.agent.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import io.yak.ops.agent.config.AgentConfiguration;
import io.yak.ops.agent.llm.LlmClient;
import io.yak.ops.agent.service.AgentChatService;
import io.yak.ops.agent.service.AgentEventSink;
import io.yak.ops.agent.service.ChatReply;
import io.yak.ops.agent.tool.AgentToolContext;
import io.yak.ops.agent.tool.AgentToolRegistry;
import io.yak.ops.dao.entity.User;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
public class AgentChatServiceImpl implements AgentChatService {

    private static final String SYSTEM_PROMPT_PATH = "prompts/system.md";

    private static final int STEP_SUMMARY_MAX_CHARS = 800;

    private final LlmClient llmClient;

    private final AgentToolRegistry toolRegistry;

    private final AgentConfiguration configuration;

    private String systemPrompt;

    @PostConstruct
    void loadSystemPrompt() {
        try {
            systemPrompt = StreamUtils.copyToString(
                    new ClassPathResource(SYSTEM_PROMPT_PATH).getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load agent system prompt from " + SYSTEM_PROMPT_PATH, ex);
        }
    }

    @Override
    public ChatReply chat(User user, long projectCode, String message) {
        return chat(user, projectCode, message, Collections.emptyList(), AgentEventSink.NOOP);
    }

    @Override
    public ChatReply chat(User user, long projectCode, String message,
                          List<LlmClient.Message> history, AgentEventSink sink) {
        AgentToolContext context = new AgentToolContext(user, projectCode);
        List<LlmClient.Message> messages = new ArrayList<>();
        messages.add(LlmClient.Message.system(withProjectContext(projectCode)));
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        messages.add(LlmClient.Message.user(message));

        int promptTokens = 0;
        int completionTokens = 0;
        long latencyMs = 0;
        List<Map<String, Object>> steps = new ArrayList<>();

        for (int round = 0; round < configuration.getMaxToolRounds(); round++) {
            LlmClient.ChatResult result = llmClient.chat(messages, toolRegistry.definitions());
            promptTokens += result.getPromptTokens();
            completionTokens += result.getCompletionTokens();
            latencyMs += result.getLatencyMs();

            if (result.getToolCalls() == null || result.getToolCalls().isEmpty()) {
                return ChatReply.builder()
                        .answer(result.getContent())
                        .steps(steps)
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .latencyMs(latencyMs)
                        .build();
            }

            messages.add(LlmClient.Message.assistant(result.getContent(), result.getToolCalls()));
            for (LlmClient.ToolCall call : result.getToolCalls()) {
                sink.toolStarted(call.getName(), call.getArguments());
                long toolStartedAt = System.currentTimeMillis();
                String output = toolRegistry.invoke(call.getName(), context, call.getArguments());
                long durationMs = System.currentTimeMillis() - toolStartedAt;
                sink.toolFinished(call.getName(), truncate(output), durationMs);
                steps.add(step(call.getName(), call.getArguments(), output, durationMs));
                messages.add(LlmClient.Message.tool(call.getId(), output));
            }
        }

        log.warn("Agent chat reached the max tool rounds limit ({})", configuration.getMaxToolRounds());
        return ChatReply.builder()
                .answer("已达到单轮工具调用上限，请缩小问题范围后重试。")
                .steps(steps)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .latencyMs(latencyMs)
                .build();
    }

    private String withProjectContext(long projectCode) {
        return systemPrompt + "\n\n当前项目编码（projectCode）：" + projectCode
                + "。所有工具调用都使用该项目的权限范围，无需向用户询问项目编码。";
    }

    private Map<String, Object> step(String tool, String arguments, String output, long durationMs) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("tool", tool);
        step.put("arguments", arguments);
        step.put("resultSummary", truncate(output));
        step.put("durationMs", durationMs);
        return step;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= STEP_SUMMARY_MAX_CHARS
                ? value
                : value.substring(0, STEP_SUMMARY_MAX_CHARS) + "...(truncated)";
    }
}
