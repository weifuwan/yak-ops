package io.yak.ops.agent.dao.repository.impl;

import io.yak.ops.agent.dao.entity.AgentToolCall;
import io.yak.ops.agent.dao.mapper.AgentToolCallMapper;
import io.yak.ops.agent.dao.repository.AgentToolCallDao;
import io.yak.ops.dao.repository.BaseDao;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
public class AgentToolCallDaoImpl extends BaseDao<AgentToolCall, AgentToolCallMapper> implements AgentToolCallDao {

    public AgentToolCallDaoImpl(@NonNull AgentToolCallMapper agentToolCallMapper) {
        super(agentToolCallMapper);
    }
}
