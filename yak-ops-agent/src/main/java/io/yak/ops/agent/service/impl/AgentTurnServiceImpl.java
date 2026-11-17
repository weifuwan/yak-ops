package io.yak.ops.agent.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.yak.ops.agent.dao.entity.AgentMessage;
import io.yak.ops.agent.dao.entity.AgentSession;
import io.yak.ops.agent.dao.entity.AgentToolCall;
import io.yak.ops.agent.dao.entity.AgentTurn;
import io.yak.ops.agent.dao.repository.AgentMessageDao;
import io.yak.ops.agent.dao.repository.AgentSessionDao;
import io.yak.ops.agent.dao.repository.AgentToolCallDao;
import io.yak.ops.agent.dao.repository.AgentTurnDao;
import io.yak.ops.agent.event.AgentEvent;
import io.yak.ops.agent.event.AgentEventBroker;
import io.yak.ops.agent.llm.LlmClient;
import io.yak.ops.agent.service.AgentChatService;
import io.yak.ops.agent.service.AgentEventSink;
import io.yak.ops.agent.service.AgentTurnExecutor;
import io.yak.ops.agent.service.AgentTurnService;
import io.yak.ops.agent.service.ChatReply;
import io.yak.ops.agent.service.TurnSubmission;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.dao.entity.User;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
public class AgentTurnServiceImpl implements AgentTurnService {

    private static final String STATUS_QUEUED = "QUEUED";

    private static final String STATUS_RUNNING = "RUNNING";

    private static final String STATUS_COMPLETED = "COMPLETED";

    private static final String STATUS_FAILED = "FAILED";

    private static final String ROLE_USER = "user";

    private static final String ROLE_ASSISTANT = "assistant";

    private static final int HISTORY_LIMIT = 10;

    private static final int HISTORY_CONTENT_MAX_CHARS = 2000;

    private static final int TITLE_MAX_CHARS = 40;

    private final AgentSessionDao sessionDao;

    private final AgentTurnDao turnDao;

    private final AgentMessageDao messageDao;

    private final AgentToolCallDao toolCallDao;

    private final AgentChatService chatService;

    private final AgentTurnExecutor turnExecutor;

    private final AgentEventBroker eventBroker;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TurnSubmission submit(User user, long projectCode, String sessionId, String message) {
        if (turnDao.countActiveByUser(user.getId()) > 0) {
            throw new ServiceException(Status.INTERNAL_SERVER_ERROR_ARGS, "已有正在处理的对话，请等待完成后再提问");
        }

        AgentSession session = resolveSession(user, sessionId, message);
        Date now = new Date();
        AgentTurn turn = AgentTurn.builder()
                .id(newId())
                .sessionId(session.getId())
                .userId(user.getId())
                .projectCode(projectCode)
                .status(STATUS_QUEUED)
                .contextJson("{\"projectCode\":" + projectCode + "}")
                .createTime(now)
                .createBy(user.getUserName())
                .updateTime(now)
                .updateBy(user.getUserName())
                .build();
        turnDao.insert(turn);
        messageDao.insert(message(session.getId(), turn.getId(), ROLE_USER, message, user.getUserName(), now));

        dispatchAfterCommit(user, turn, message);
        return TurnSubmission.builder()
                .sessionId(session.getId())
                .turnId(turn.getId())
                .status(STATUS_QUEUED)
                .build();
    }

    /**
     * The worker must be started only after the surrounding transaction commits,
     * otherwise the CAS from QUEUED to RUNNING cannot see the inserted row.
     */
    private void dispatchAfterCommit(User user, AgentTurn turn, String message) {
        Runnable dispatch = () -> {
            boolean accepted = turnExecutor.execute(() -> runTurn(user, turn, message));
            if (!accepted) {
                log.warn("Agent turn {} rejected by the executor", turn.getId());
                markFailed(turn.getId(), "AGENT_POOL_FULL", "系统繁忙，请稍后重试", user.getUserName());
                eventBroker.publish(turn.getId(), AgentEvent.TURN_FAILED,
                        payload("errorCode", "AGENT_POOL_FULL", "errorMessage", "系统繁忙，请稍后重试"));
                eventBroker.complete(turn.getId());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    @Override
    public SseEmitter subscribe(User user, String turnId) {
        AgentTurn turn = requireTurn(user, turnId);
        SseEmitter emitter = eventBroker.subscribe(turnId);
        if (isTerminal(turn.getStatus())) {
            Map<String, Object> payload = terminalPayload(turn);
            eventBroker.publish(turnId, isCompleted(turn.getStatus())
                    ? AgentEvent.TURN_COMPLETED
                    : AgentEvent.TURN_FAILED, payload);
            eventBroker.complete(turnId);
        }
        return emitter;
    }

    @Override
    public Map<String, Object> getTurnResult(User user, String turnId) {
        return terminalPayload(requireTurn(user, turnId));
    }

    @Override
    public List<Map<String, Object>> listSessionMessages(User user, String sessionId, int limit) {
        AgentSession session = sessionDao.queryById(sessionId);
        if (session == null || !Objects.equals(session.getUserId(), user.getId())) {
            throw new ServiceException(Status.RESOURCE_NOT_EXIST_OR_NO_PERMISSION);
        }
        List<AgentMessage> messages = messageDao.queryRecentBySession(sessionId, Math.max(1, Math.min(100, limit)));
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentMessage item : messages) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("turnId", item.getTurnId());
            row.put("role", item.getRole());
            row.put("content", item.getContent());
            row.put("createTime", item.getCreateTime());
            result.add(row);
        }
        return result;
    }

    private void runTurn(User user, AgentTurn turn, String message) {
        if (!turnDao.casStatus(turn.getId(), STATUS_QUEUED, STATUS_RUNNING)) {
            return;
        }
        eventBroker.publish(turn.getId(), AgentEvent.TURN_STARTED, payload("status", STATUS_RUNNING));
        try {
            List<LlmClient.Message> history = loadHistory(turn.getSessionId(), turn.getId());
            AgentEventSink sink = new AgentEventSink() {

                @Override
                public void toolStarted(String tool, String arguments) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("tool", tool);
                    payload.put("arguments", arguments);
                    eventBroker.publish(turn.getId(), AgentEvent.TOOL_STARTED, payload);
                }

                @Override
                public void toolFinished(String tool, String resultSummary, long durationMs) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("tool", tool);
                    payload.put("resultSummary", resultSummary);
                    payload.put("durationMs", durationMs);
                    eventBroker.publish(turn.getId(), AgentEvent.TOOL_FINISHED, payload);
                }
            };

            ChatReply reply = chatService.chat(user, turn.getProjectCode(), message, history, sink);
            Date now = new Date();
            messageDao.insert(message(turn.getSessionId(), turn.getId(), ROLE_ASSISTANT,
                    reply.getAnswer(), user.getUserName(), now));
            persistToolCalls(turn, reply.getSteps(), user.getUserName(), now);
            completeTurn(turn.getId(), reply, user.getUserName());

            Map<String, Object> payload = terminalPayload(turnDao.queryById(turn.getId()));
            eventBroker.publish(turn.getId(), AgentEvent.TURN_COMPLETED, payload);
        } catch (Exception ex) {
            log.error("Agent turn {} failed", turn.getId(), ex);
            markFailed(turn.getId(), "AGENT_TURN_FAILED", ex.getMessage(), user.getUserName());
            eventBroker.publish(turn.getId(), AgentEvent.TURN_FAILED,
                    payload("errorCode", "AGENT_TURN_FAILED", "errorMessage", ex.getMessage()));
        } finally {
            eventBroker.complete(turn.getId());
        }
    }

    private AgentSession resolveSession(User user, String sessionId, String message) {
        Date now = new Date();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            AgentSession session = AgentSession.builder()
                    .id(newId())
                    .userId(user.getId())
                    .title(truncate(message, TITLE_MAX_CHARS))
                    .status("ACTIVE")
                    .createTime(now)
                    .createBy(user.getUserName())
                    .updateTime(now)
                    .updateBy(user.getUserName())
                    .build();
            sessionDao.insert(session);
            return session;
        }
        AgentSession session = sessionDao.queryById(sessionId.trim());
        if (session == null || !Objects.equals(session.getUserId(), user.getId())) {
            throw new ServiceException(Status.RESOURCE_NOT_EXIST_OR_NO_PERMISSION);
        }
        return session;
    }

    private AgentTurn requireTurn(User user, String turnId) {
        AgentTurn turn = turnDao.queryById(turnId);
        if (turn == null || !Objects.equals(turn.getUserId(), user.getId())) {
            throw new ServiceException(Status.RESOURCE_NOT_EXIST_OR_NO_PERMISSION);
        }
        return turn;
    }

    private void completeTurn(String turnId, ChatReply reply, String operator) {
        if (!turnDao.casStatus(turnId, STATUS_RUNNING, STATUS_COMPLETED)) {
            return;
        }
        turnDao.updateById(AgentTurn.builder()
                .id(turnId)
                .llmLatencyMs((int) reply.getLatencyMs())
                .promptTokens(reply.getPromptTokens())
                .completionTokens(reply.getCompletionTokens())
                .updateTime(new Date())
                .updateBy(operator)
                .build());
    }

    private void markFailed(String turnId, String errorCode, String errorMessage, String operator) {
        boolean moved = turnDao.casStatus(turnId, STATUS_RUNNING, STATUS_FAILED)
                || turnDao.casStatus(turnId, STATUS_QUEUED, STATUS_FAILED);
        if (!moved) {
            return;
        }
        turnDao.updateById(AgentTurn.builder()
                .id(turnId)
                .errorCode(errorCode)
                .errorMessage(truncate(errorMessage, 900))
                .updateTime(new Date())
                .updateBy(operator)
                .build());
    }

    private void persistToolCalls(AgentTurn turn, List<Map<String, Object>> steps, String operator, Date now) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        for (Map<String, Object> step : steps) {
            toolCallDao.insert(AgentToolCall.builder()
                    .id(newId())
                    .sessionId(turn.getSessionId())
                    .turnId(turn.getId())
                    .toolName(String.valueOf(step.get("tool")))
                    .argumentsJson(truncate(String.valueOf(step.get("arguments")), 4000))
                    .resultSummary(truncate(String.valueOf(step.get("resultSummary")), 4000))
                    .durationMs(step.get("durationMs") == null ? null : ((Number) step.get("durationMs")).intValue())
                    .status("SUCCESS")
                    .createTime(now)
                    .createBy(operator)
                    .updateTime(now)
                    .updateBy(operator)
                    .build());
        }
    }

    private List<LlmClient.Message> loadHistory(String sessionId, String currentTurnId) {
        List<AgentMessage> recent = messageDao.queryRecentBySession(sessionId, HISTORY_LIMIT + 2);
        List<LlmClient.Message> history = new ArrayList<>();
        for (AgentMessage item : recent) {
            if (Objects.equals(item.getTurnId(), currentTurnId)) {
                continue;
            }
            String content = truncate(item.getContent(), HISTORY_CONTENT_MAX_CHARS);
            if (ROLE_USER.equals(item.getRole())) {
                history.add(LlmClient.Message.user(content));
            } else if (ROLE_ASSISTANT.equals(item.getRole())) {
                history.add(LlmClient.Message.assistant(content, null));
            }
        }
        return history;
    }

    private Map<String, Object> terminalPayload(AgentTurn turn) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("turnId", turn.getId());
        payload.put("sessionId", turn.getSessionId());
        payload.put("status", turn.getStatus());
        if (turn.getErrorCode() != null) {
            payload.put("errorCode", turn.getErrorCode());
            payload.put("errorMessage", turn.getErrorMessage());
        }
        if (isCompleted(turn.getStatus())) {
            List<AgentMessage> messages = messageDao.queryByTurn(turn.getId());
            for (AgentMessage item : messages) {
                if (ROLE_ASSISTANT.equals(item.getRole())) {
                    payload.put("answer", item.getContent());
                }
            }
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("promptTokens", turn.getPromptTokens());
            usage.put("completionTokens", turn.getCompletionTokens());
            usage.put("latencyMs", turn.getLlmLatencyMs());
            payload.put("usage", usage);
        }
        return payload;
    }

    private boolean isTerminal(String status) {
        return isCompleted(status) || STATUS_FAILED.equals(status) || "CANCELLED".equals(status);
    }

    private boolean isCompleted(String status) {
        return STATUS_COMPLETED.equals(status);
    }

    private AgentMessage message(String sessionId, String turnId, String role, String content,
                                 String operator, Date now) {
        return AgentMessage.builder()
                .id(newId())
                .sessionId(sessionId)
                .turnId(turnId)
                .role(role)
                .content(content)
                .createTime(now)
                .createBy(operator)
                .updateTime(now)
                .updateBy(operator)
                .build();
    }

    private Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            payload.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return payload;
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String truncate(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "...(truncated)";
    }
}
