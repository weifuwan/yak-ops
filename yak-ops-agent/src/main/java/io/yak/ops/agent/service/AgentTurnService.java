package io.yak.ops.agent.service;

import java.util.List;
import java.util.Map;

import io.yak.ops.dao.entity.User;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentTurnService {

    TurnSubmission submit(User user, long projectCode, String sessionId, String message);

    SseEmitter subscribe(User user, String turnId);

    Map<String, Object> getTurnResult(User user, String turnId);

    List<Map<String, Object>> listSessionMessages(User user, String sessionId, int limit);
}
