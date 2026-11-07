package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.SerialCommand;
import io.yak.ops.dao.model.SerialCommandDto;

import java.util.List;

public interface SerialCommandDao extends IDao<SerialCommand> {

    List<SerialCommandDto> fetchSerialCommands(int fetchSize);

    boolean tryLaunch(int id);

    int deleteByWorkflowInstanceId(Integer workflowInstanceId);

}
