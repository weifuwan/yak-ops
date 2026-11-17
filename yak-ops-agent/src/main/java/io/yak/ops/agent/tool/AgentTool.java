package io.yak.ops.agent.tool;

import java.util.Map;

/**
 * A read-only capability exposed to the model. Implementations must never write.
 */
public interface AgentTool {

    String name();

    String description();

    Map<String, Object> parameterSchema();

    String execute(AgentToolContext context, Map<String, Object> arguments) throws Exception;
}
