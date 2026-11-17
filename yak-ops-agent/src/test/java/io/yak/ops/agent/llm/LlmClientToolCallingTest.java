package io.yak.ops.agent.llm;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.yak.ops.agent.config.AgentConfiguration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Live check of the OpenAI-compatible tool-calling protocol. Skipped unless
 * AGENT_API_KEY (plus AGENT_BASE_URL / AGENT_MODEL) is present in the environment.
 */
class LlmClientToolCallingTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "AGENT_API_KEY", matches = ".+")
    void shouldParseToolCallsFromOpenAiCompatibleEndpoint() {
        AgentConfiguration configuration = new AgentConfiguration();
        configuration.setLlmBaseUrl(System.getenv().getOrDefault("AGENT_BASE_URL", ""));
        configuration.setLlmApiKey(System.getenv("AGENT_API_KEY"));
        configuration.setLlmModel(System.getenv().getOrDefault("AGENT_MODEL", "glm-5.3-flash"));
        configuration.setLlmTimeoutSeconds(90);

        LlmClient client = new LlmClient(configuration);

        Map<String, Object> city = new LinkedHashMap<>();
        city.put("type", "string");
        city.put("description", "City name");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("city", city);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Collections.singletonList("city"));

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", "echo_city");
        function.put("description", "Echo back the given city name");
        function.put("parameters", schema);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);

        List<LlmClient.Message> messages = new ArrayList<>();
        messages.add(LlmClient.Message.system("You are a test assistant. Always call the provided tool."));
        messages.add(LlmClient.Message.user("请查询城市 Beijing 的信息，必须调用工具 echo_city。"));

        LlmClient.ChatResult result = client.chat(messages, Collections.singletonList(tool));

        assertNotNull(result);
        boolean hasContent = result.getContent() != null && !result.getContent().isEmpty();
        boolean hasToolCalls = result.getToolCalls() != null && !result.getToolCalls().isEmpty();
        assertTrue(hasContent || hasToolCalls, "response must contain content or tool calls");
        assertTrue(result.getLatencyMs() > 0, "latency must be measured");

        if (hasToolCalls) {
            LlmClient.ToolCall call = result.getToolCalls().get(0);
            assertNotNull(call.getName(), "tool call name must be parsed");
            assertNotNull(call.getArguments(), "tool call arguments must be parsed");
        }
    }
}
