package io.yak.ops.agent.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import io.yak.ops.agent.service.AgentChatService;
import io.yak.ops.agent.service.ChatReply;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent")
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
public class AgentChatController extends BaseController {

    private final AgentChatService agentChatService;

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(
            @RequestAttribute(Constants.SESSION_USER) User loginUser,
            @RequestBody Map<String, Object> request) {
        String message = request.get("message") == null ? null : String.valueOf(request.get("message")).trim();
        Object projectCodeRaw = request.get("projectCode");
        if (message == null || message.isEmpty()) {
            return Result.errorWithArgs(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "message");
        }
        if (projectCodeRaw == null || String.valueOf(projectCodeRaw).trim().isEmpty()) {
            return Result.errorWithArgs(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "projectCode");
        }

        long projectCode;
        try {
            projectCode = Long.parseLong(String.valueOf(projectCodeRaw).trim());
        } catch (NumberFormatException ex) {
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
}
