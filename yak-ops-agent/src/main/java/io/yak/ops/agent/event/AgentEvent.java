package io.yak.ops.agent.event;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentEvent {

    public static final String TURN_STARTED = "TURN_STARTED";

    public static final String TOOL_STARTED = "TOOL_STARTED";

    public static final String TOOL_FINISHED = "TOOL_FINISHED";

    public static final String TURN_COMPLETED = "TURN_COMPLETED";

    public static final String TURN_FAILED = "TURN_FAILED";

    private long seq;

    private String type;

    private String turnId;

    private Map<String, Object> payload;
}
