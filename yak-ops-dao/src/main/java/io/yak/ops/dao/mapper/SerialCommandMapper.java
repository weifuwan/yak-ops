package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.SerialCommand;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface SerialCommandMapper extends BaseMapper<SerialCommand> {

    List<SerialCommand> fetchSerialCommands(@Param("fetchSize") int fetchSize);

    int deleteByWorkflowInstanceId(@Param("workflowInstanceId") int workflowInstanceId);

}
