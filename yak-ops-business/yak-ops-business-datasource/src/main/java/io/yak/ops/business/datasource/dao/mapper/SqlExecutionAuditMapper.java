package io.yak.ops.business.datasource.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditPO;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditQuery;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditSummaryRow;
import io.yak.ops.business.datasource.dao.model.SqlStatementTypeCountRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for execution-level SQL audit records and aggregates. */
@Mapper
public interface SqlExecutionAuditMapper extends BaseMapper<SqlExecutionAuditPO> {

    IPage<SqlExecutionAuditPO> selectAuditPage(
            Page<SqlExecutionAuditPO> page, @Param("query") SqlExecutionAuditQuery query);

    SqlExecutionAuditSummaryRow selectAuditSummary(@Param("query") SqlExecutionAuditQuery query);

    Long selectP95DurationMs(@Param("query") SqlExecutionAuditQuery query);

    List<SqlStatementTypeCountRow> selectStatementTypeCounts(@Param("query") SqlExecutionAuditQuery query);
}
