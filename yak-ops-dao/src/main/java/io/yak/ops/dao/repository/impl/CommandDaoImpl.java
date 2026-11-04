package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.mapper.CommandMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.CommandDao;

import java.util.List;

import org.springframework.stereotype.Repository;

@Repository
public class CommandDaoImpl extends BaseDao<Command, CommandMapper> implements CommandDao {

    public CommandDaoImpl(CommandMapper commandMapper) {
        super(commandMapper);
    }

    @Override
    public List<Command> queryCommandByIdSlot(int currentSlotIndex, int totalSlot, int idStep, int fetchNum) {
        return mybatisMapper.queryCommandByIdSlot(currentSlotIndex, totalSlot, idStep, fetchNum);
    }

}
