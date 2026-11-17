package io.yak.ops.agent.dao.repository;

import java.util.List;

import io.yak.ops.agent.dao.entity.AgentSession;
import io.yak.ops.dao.repository.IDao;

public interface AgentSessionDao extends IDao<AgentSession> {

    List<AgentSession> queryRecentByUser(Integer userId, int limit);
}
