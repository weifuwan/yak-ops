package io.yak.ops.agent.dao.repository.impl;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.yak.ops.agent.dao.entity.AgentTurn;
import io.yak.ops.agent.dao.mapper.AgentTurnMapper;
import io.yak.ops.agent.dao.repository.AgentTurnDao;
import io.yak.ops.dao.repository.BaseDao;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.NonNull;

@Repository
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
public class AgentTurnDaoImpl extends BaseDao<AgentTurn, AgentTurnMapper> implements AgentTurnDao {

    public AgentTurnDaoImpl(@NonNull AgentTurnMapper agentTurnMapper) {
        super(agentTurnMapper);
    }

    @Override
    public boolean casStatus(String id, String fromStatus, String toStatus) {
        return mybatisMapper.update(null, Wrappers.<AgentTurn>lambdaUpdate()
                .set(AgentTurn::getStatus, toStatus)
                .set(AgentTurn::getUpdateTime, new Date())
                .eq(AgentTurn::getId, id)
                .eq(AgentTurn::getStatus, fromStatus)) > 0;
    }

    @Override
    public long countActiveByUser(Integer userId) {
        Long count = mybatisMapper.selectCount(Wrappers.<AgentTurn>lambdaQuery()
                .eq(AgentTurn::getUserId, userId)
                .in(AgentTurn::getStatus, Arrays.asList("QUEUED", "RUNNING")));
        return count == null ? 0L : count;
    }

    @Override
    public List<AgentTurn> queryRecentBySession(String sessionId, int limit) {
        IPage<AgentTurn> page = mybatisMapper.selectPage(new Page<>(1, limit),
                Wrappers.<AgentTurn>lambdaQuery()
                        .eq(AgentTurn::getSessionId, sessionId)
                        .orderByDesc(AgentTurn::getCreateTime));
        return page.getRecords();
    }
}
