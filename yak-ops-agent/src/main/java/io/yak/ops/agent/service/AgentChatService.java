package io.yak.ops.agent.service;

import io.yak.ops.dao.entity.User;

public interface AgentChatService {

    ChatReply chat(User user, long projectCode, String message);
}
