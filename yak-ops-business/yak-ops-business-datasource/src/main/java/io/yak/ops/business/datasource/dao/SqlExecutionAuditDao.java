package io.yak.ops.business.datasource.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditPO;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditQuery;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditSummaryRow;
import io.yak.ops.business.datasource.dao.model.SqlStatementExecutionAuditPO;
import io.yak.ops.business.datasource.dao.model.SqlStatementTypeCountRow;
import java.util.List;

/** Persistence boundary for SQL execution observability. */
public interface SqlExecutionAuditDao {

  void insertExecution(SqlExecutionAuditPO execution);

  void insertStatements(List<SqlStatementExecutionAuditPO> statements);

  IPage<SqlExecutionAuditPO> selectPage(SqlExecutionAuditQuery query);

  SqlExecutionAuditPO selectByExecutionId(String executionId);

  List<SqlStatementExecutionAuditPO> selectStatements(String executionId);

  SqlExecutionAuditSummaryRow selectSummary(SqlExecutionAuditQuery query);

  long selectP95DurationMs(SqlExecutionAuditQuery query);

  List<SqlStatementTypeCountRow> selectStatementTypeCounts(SqlExecutionAuditQuery query);
}
