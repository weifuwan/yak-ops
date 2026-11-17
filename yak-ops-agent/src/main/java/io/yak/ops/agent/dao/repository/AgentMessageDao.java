package io.yak.ops.agent.dao.repository;

import java.util.List;

import io.yak.ops.agent.dao.entity.AgentMessage;
import io.yak.ops.dao.repository.IDao;

public interface AgentMessageDao extends IDao<AgentMessage> {

    List<AgentMessage> queryRecentBySession(String sessionId, int limit);

    List<AgentMessage> queryByTurn(String turnId);
}
