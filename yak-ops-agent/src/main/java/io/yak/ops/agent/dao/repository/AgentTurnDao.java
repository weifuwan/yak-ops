package io.yak.ops.agent.dao.repository;

import java.util.List;

import io.yak.ops.agent.dao.entity.AgentTurn;
import io.yak.ops.dao.repository.IDao;

public interface AgentTurnDao extends IDao<AgentTurn> {

    /**
     * Compare-and-set the turn status: only one winner may move a turn forward.
     */
    boolean casStatus(String id, String fromStatus, String toStatus);

    long countActiveByUser(Integer userId);

    List<AgentTurn> queryRecentBySession(String sessionId, int limit);
}
