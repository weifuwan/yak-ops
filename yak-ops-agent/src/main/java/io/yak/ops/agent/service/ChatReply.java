package io.yak.ops.agent.service;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatReply {

    private String answer;

    private List<Map<String, Object>> steps;

    private int promptTokens;

    private int completionTokens;

    private long latencyMs;
}
