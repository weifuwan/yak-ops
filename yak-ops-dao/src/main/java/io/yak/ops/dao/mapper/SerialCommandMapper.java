package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.SerialCommand;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface SerialCommandMapper extends BaseMapper<SerialCommand> {

    List<SerialCommand> fetchSerialCommands(@Param("fetchSize") int fetchSize);

    int deleteByWorkflowInstanceId(@Param("workflowInstanceId") int workflowInstanceId);

    @Update("UPDATE t_yak_ops_serial_command SET state = 1, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND state = 0")
    int launchIfWaiting(@Param("id") int id);

}
