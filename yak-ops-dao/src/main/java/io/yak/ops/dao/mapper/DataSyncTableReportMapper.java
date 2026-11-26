package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.DataSyncTableReport;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface DataSyncTableReportMapper extends BaseMapper<DataSyncTableReport> {

    @Select("SELECT * FROM t_yak_ops_data_sync_table_report WHERE execution_id = #{executionId}")
    List<DataSyncTableReport> queryByExecutionId(@Param("executionId") String executionId);

    @Select("SELECT * FROM t_yak_ops_data_sync_table_report "
            + "WHERE execution_id = #{executionId} AND table_key = #{tableKey} LIMIT 1")
    DataSyncTableReport queryByExecutionIdAndTableKey(
            @Param("executionId") String executionId, @Param("tableKey") String tableKey);
}
