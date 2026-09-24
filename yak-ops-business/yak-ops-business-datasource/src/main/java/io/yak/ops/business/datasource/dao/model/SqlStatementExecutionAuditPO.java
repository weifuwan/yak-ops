package io.yak.ops.business.datasource.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.core.execution.sql.SqlExecutionResultType;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import java.time.LocalDateTime;
import lombok.Data;

/** Persisted statement-level SQL audit metadata. */
@Data
@TableName("yak_ops_sql_statement_execution")
public class SqlStatementExecutionAuditPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String executionId;
    private String statementId;
    private Integer statementIndex;
    private SqlStatementType statementType;
    private String sqlFingerprint;
    private String sqlPreview;
    private SqlStatementStatus status;
    private SqlExecutionResultType resultType;
    private Long returnedRows;
    private Long affectedRows;
    private Boolean truncated;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String errorMessage;
    private LocalDateTime createTime;
}
