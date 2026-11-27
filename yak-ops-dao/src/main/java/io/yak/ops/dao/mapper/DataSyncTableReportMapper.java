package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.DataSyncTableReport;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface DataSyncTableReportMapper extends BaseMapper<DataSyncTableReport> {

    @Update({
            "<script>",
            "UPDATE t_yak_ops_data_sync_table_report",
            "SET latest_metrics_json = NULL, latest_sequence = #{sequence},",
            "latest_collected_at = #{collectedAt}, update_time = #{updateTime}",
            "<if test='finalReport'>, final_metrics_json = NULL, final_collected_at = #{collectedAt}</if>",
            "WHERE id = #{id}",
            "</script>"
    })
    int clearSnapshotFields(
            @Param("id") Long id,
            @Param("sequence") long sequence,
            @Param("collectedAt") long collectedAt,
            @Param("updateTime") Date updateTime,
            @Param("finalReport") boolean finalReport);

    @Select("SELECT * FROM t_yak_ops_data_sync_table_report WHERE execution_id = #{executionId}")
    List<DataSyncTableReport> queryByExecutionId(@Param("executionId") String executionId);

    @Select("SELECT * FROM t_yak_ops_data_sync_table_report "
            + "WHERE execution_id = #{executionId} AND table_key = #{tableKey} LIMIT 1")
    DataSyncTableReport queryByExecutionIdAndTableKey(
            @Param("executionId") String executionId, @Param("tableKey") String tableKey);

    @Select("SELECT * FROM t_yak_ops_data_sync_table_report "
            + "WHERE execution_id = #{executionId} "
            + "AND ((source_table IS NULL AND sink_table = #{sinkTable}) "
            + "OR (sink_table IS NULL AND source_table = #{sourceTable})) LIMIT 1")
    DataSyncTableReport queryByExecutionIdAndPartialTableMapping(
            @Param("executionId") String executionId,
            @Param("sourceTable") String sourceTable,
            @Param("sinkTable") String sinkTable);
}
