package io.yak.ops.agent.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Whitelist registry of read-only tools. Only tools registered here can ever be
 * invoked, which is the second line of defence besides the interface contract.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentToolRegistry(List<AgentTool> tools) {
        Map<String, AgentTool> registered = new LinkedHashMap<>();
        for (AgentTool tool : tools) {
            registered.put(tool.name(), tool);
        }
        this.tools = Collections.unmodifiableMap(registered);
        log.info("Registered {} read-only agent tools: {}", this.tools.size(), this.tools.keySet());
    }

    public List<Map<String, Object>> definitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.parameterSchema());
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("type", "function");
            definition.put("function", function);
            definitions.add(definition);
        }
        return definitions;
    }

    public String invoke(String name, AgentToolContext context, String argumentsJson) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return error("unknown tool: " + name);
        }

        Map<String, Object> arguments;
        try {
            arguments = argumentsJson == null || argumentsJson.trim().isEmpty()
                    ? new LinkedHashMap<>()
                    : objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception ex) {
            return error("invalid tool arguments: " + ex.getMessage());
        }

        try {
            return tool.execute(context, arguments);
        } catch (Exception ex) {
            log.warn("Agent tool {} failed", name, ex);
            return error(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private String error(String message) {
        return "{\"error\":\"" + message.replace("\"", "'") + "\"}";
    }
}
