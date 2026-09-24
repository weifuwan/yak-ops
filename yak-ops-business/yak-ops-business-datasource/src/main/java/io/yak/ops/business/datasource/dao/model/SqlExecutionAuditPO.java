package io.yak.ops.business.datasource.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlTransactionMode;
import java.time.LocalDateTime;
import lombok.Data;

/** Persisted execution-level SQL audit metadata. */
@Data
@TableName("yak_ops_sql_execution")
public class SqlExecutionAuditPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String executionId;
  private String dataSourceId;
  private SqlExecutionCaller caller;
  private String callerReference;
  private String operatorName;
  private SqlTransactionMode transactionMode;
  private SqlExecutionStatus status;
  private Integer statementCount;
  private Integer succeededStatementCount;
  private Long returnedRows;
  private Long affectedRows;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
  private Long durationMs;
  private String errorMessage;
  private LocalDateTime createTime;
}
