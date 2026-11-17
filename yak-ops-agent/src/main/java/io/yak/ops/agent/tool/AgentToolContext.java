package io.yak.ops.agent.tool;

import io.yak.ops.dao.entity.User;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Explicit execution context. The authenticated user travels as a parameter because
 * tool execution happens outside the request thread (no RequestContextHolder).
 */
@Getter
@RequiredArgsConstructor
public class AgentToolContext {

    private final User user;

    private final long projectCode;
}
