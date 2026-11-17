package io.yak.ops.agent.dao.repository.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.yak.ops.agent.dao.entity.AgentMessage;
import io.yak.ops.agent.dao.mapper.AgentMessageMapper;
import io.yak.ops.agent.dao.repository.AgentMessageDao;
import io.yak.ops.dao.repository.BaseDao;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.NonNull;

@Repository
public class AgentMessageDaoImpl extends BaseDao<AgentMessage, AgentMessageMapper> implements AgentMessageDao {

    public AgentMessageDaoImpl(@NonNull AgentMessageMapper agentMessageMapper) {
        super(agentMessageMapper);
    }

    @Override
    public List<AgentMessage> queryRecentBySession(String sessionId, int limit) {
        IPage<AgentMessage> page = mybatisMapper.selectPage(new Page<>(1, limit),
                Wrappers.<AgentMessage>lambdaQuery()
                        .eq(AgentMessage::getSessionId, sessionId)
                        .orderByDesc(AgentMessage::getCreateTime));
        List<AgentMessage> records = new ArrayList<>(page.getRecords());
        Collections.reverse(records);
        return records;
    }

    @Override
    public List<AgentMessage> queryByTurn(String turnId) {
        return mybatisMapper.selectList(Wrappers.<AgentMessage>lambdaQuery()
                .eq(AgentMessage::getTurnId, turnId)
                .orderByAsc(AgentMessage::getCreateTime));
    }
}
