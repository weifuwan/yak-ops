package io.yak.ops.agent.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.yak.ops.agent.service.AgentChatService;
import io.yak.ops.agent.service.AgentTurnService;
import io.yak.ops.agent.service.ChatReply;
import io.yak.ops.agent.service.TurnSubmission;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent")
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
public class AgentChatController extends BaseController {

    private final AgentChatService agentChatService;

    private final AgentTurnService agentTurnService;

    /**
     * Asynchronous turn submission: persists the turn and returns immediately.
     */
    @PostMapping("/chat/turns")
    public Result<Map<String, Object>> submitTurn(
            @RequestAttribute(Constants.SESSION_USER) User loginUser,
            @RequestBody Map<String, Object> request) {
        String message = stringValue(request.get("message"));
        if (message == null) {
            return Result.errorWithArgs(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "message");
        }
        Long projectCode = longValue(request.get("projectCode"));
        if (projectCode == null) {
            return Result.errorWithArgs(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "projectCode");
        }

        TurnSubmission submission = agentTurnService.submit(loginUser, projectCode,
                stringValue(request.get("sessionId")), message);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", submission.getSessionId());
        payload.put("turnId", submission.getTurnId());
        payload.put("status", submission.getStatus());
        return Result.success(payload);
    }

    /**
     * SSE stream for one turn. Replays the terminal state when the turn already finished.
     */
    @GetMapping("/turns/{turnId}/events")
    public SseEmitter turnEvents(@RequestAttribute(Constants.SESSION_USER) User loginUser,
                                 @PathVariable String turnId) {
        return agentTurnService.subscribe(loginUser, turnId);
    }

    @GetMapping("/turns/{turnId}")
    public Result<Map<String, Object>> turnResult(@RequestAttribute(Constants.SESSION_USER) User loginUser,
                                                  @PathVariable String turnId) {
        return Result.success(agentTurnService.getTurnResult(loginUser, turnId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<Map<String, Object>>> sessionMessages(
            @RequestAttribute(Constants.SESSION_USER) User loginUser,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(agentTurnService.listSessionMessages(loginUser, sessionId, limit));
    }

    /**
     * Synchronous debug endpoint kept for scripts and smoke tests.
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(
            @RequestAttribute(Constants.SESSION_USER) User loginUser,
            @RequestBody Map<String, Object> request) {
        String message = stringValue(request.get("message"));
        if (message == null) {
            return Result.errorWithArgs(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "message");
        }
        Long projectCode = longValue(request.get("projectCode"));
        if (projectCode == null) {
            return Result.errorWithArgs(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "projectCode");
        }

        ChatReply reply = agentChatService.chat(loginUser, projectCode, message);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("answer", reply.getAnswer());
        payload.put("steps", reply.getSteps());
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("promptTokens", reply.getPromptTokens());
        usage.put("completionTokens", reply.getCompletionTokens());
        usage.put("latencyMs", reply.getLatencyMs());
        payload.put("usage", usage);
        return Result.success(payload);
    }

    private String stringValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private Long longValue(Object raw) {
        String value = stringValue(raw);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
