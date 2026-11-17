package io.yak.ops.agent.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TurnSubmission {

    private String sessionId;

    private String turnId;

    private String status;
}
