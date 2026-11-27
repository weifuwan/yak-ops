package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.DataSyncExecutionReport;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface DataSyncExecutionReportMapper extends BaseMapper<DataSyncExecutionReport> {

    @Select("SELECT * FROM t_yak_ops_data_sync_execution_report WHERE execution_id = #{executionId} LIMIT 1")
    DataSyncExecutionReport queryByExecutionId(@Param("executionId") String executionId);

    @Select("SELECT * FROM t_yak_ops_data_sync_execution_report "
            + "WHERE task_instance_id = #{taskInstanceId} ORDER BY submitted_at DESC, create_time DESC, id DESC LIMIT 1")
    DataSyncExecutionReport queryLatestByTaskInstanceId(@Param("taskInstanceId") int taskInstanceId);

    @Select({
            "<script>",
            "SELECT * FROM t_yak_ops_data_sync_execution_report",
            "WHERE task_instance_id IN",
            "<foreach collection='taskInstanceIds' item='taskInstanceId' open='(' separator=',' close=')'>",
            "#{taskInstanceId}",
            "</foreach>",
            "ORDER BY task_instance_id ASC, submitted_at DESC, create_time DESC, id DESC",
            "</script>"
    })
    java.util.List<DataSyncExecutionReport> queryLatestByTaskInstanceIds(
            @Param("taskInstanceIds") java.util.List<Integer> taskInstanceIds);
}
