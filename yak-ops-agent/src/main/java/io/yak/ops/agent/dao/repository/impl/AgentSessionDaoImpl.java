package io.yak.ops.agent.dao.repository.impl;

import java.util.List;

import io.yak.ops.agent.dao.entity.AgentSession;
import io.yak.ops.agent.dao.mapper.AgentSessionMapper;
import io.yak.ops.agent.dao.repository.AgentSessionDao;
import io.yak.ops.dao.repository.BaseDao;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.NonNull;

@Repository
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
public class AgentSessionDaoImpl extends BaseDao<AgentSession, AgentSessionMapper> implements AgentSessionDao {

    public AgentSessionDaoImpl(@NonNull AgentSessionMapper agentSessionMapper) {
        super(agentSessionMapper);
    }

    @Override
    public List<AgentSession> queryRecentByUser(Integer userId, int limit) {
        IPage<AgentSession> page = mybatisMapper.selectPage(new Page<>(1, limit),
                Wrappers.<AgentSession>lambdaQuery()
                        .eq(AgentSession::getUserId, userId)
                        .orderByDesc(AgentSession::getUpdateTime));
        return page.getRecords();
    }
}
