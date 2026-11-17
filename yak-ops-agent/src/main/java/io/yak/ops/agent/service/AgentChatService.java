package io.yak.ops.agent.service;

import java.util.List;

import io.yak.ops.agent.llm.LlmClient;
import io.yak.ops.dao.entity.User;

public interface AgentChatService {

    ChatReply chat(User user, long projectCode, String message);

    ChatReply chat(User user, long projectCode, String message,
                   List<LlmClient.Message> history, AgentEventSink sink);
}
