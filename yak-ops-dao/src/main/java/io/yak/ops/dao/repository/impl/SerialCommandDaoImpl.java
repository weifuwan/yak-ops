package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.SerialCommand;
import io.yak.ops.dao.mapper.SerialCommandMapper;
import io.yak.ops.dao.model.SerialCommandDto;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.SerialCommandDao;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class SerialCommandDaoImpl extends BaseDao<SerialCommand, SerialCommandMapper> implements SerialCommandDao {

    public SerialCommandDaoImpl(@NonNull SerialCommandMapper serialCommandMapper) {
        super(serialCommandMapper);
    }

    @Override
    public List<SerialCommandDto> fetchSerialCommands(int fetchSize) {
        return mybatisMapper.fetchSerialCommands(fetchSize).stream().map(SerialCommandDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public boolean tryLaunch(int id) {
        return mybatisMapper.launchIfWaiting(id) == 1;
    }

    @Override
    public int deleteByWorkflowInstanceId(Integer workflowInstanceId) {
        return mybatisMapper.deleteByWorkflowInstanceId(workflowInstanceId);
    }
}
