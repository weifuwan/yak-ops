package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.DataSyncExecutionReport;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface DataSyncExecutionReportMapper extends BaseMapper<DataSyncExecutionReport> {

    @Select("SELECT * FROM t_yak_ops_data_sync_execution_report WHERE execution_id = #{executionId} LIMIT 1")
    DataSyncExecutionReport queryByExecutionId(@Param("executionId") String executionId);

    @Select("SELECT * FROM t_yak_ops_data_sync_execution_report "
            + "WHERE task_instance_id = #{taskInstanceId} ORDER BY update_time DESC, id DESC LIMIT 1")
    DataSyncExecutionReport queryLatestByTaskInstanceId(@Param("taskInstanceId") int taskInstanceId);
}
