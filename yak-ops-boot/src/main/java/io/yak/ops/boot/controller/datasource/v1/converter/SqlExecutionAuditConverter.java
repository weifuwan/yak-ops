package io.yak.ops.boot.controller.datasource.v1.converter;

import io.yak.ops.common.PageData;
import io.yak.ops.common.PagingData;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.execution.audit.SqlExecutionAuditCriteria;
import io.yak.ops.business.datasource.execution.audit.SqlExecutionAuditDetail;
import io.yak.ops.business.datasource.execution.audit.SqlExecutionAuditRecord;
import io.yak.ops.business.datasource.execution.audit.SqlExecutionAuditSummary;
import io.yak.ops.business.datasource.execution.audit.SqlStatementAuditRecord;
import io.yak.ops.common.bean.dto.observability.SqlExecutionAuditQueryDTO;
import io.yak.ops.common.bean.vo.observability.SqlExecutionAuditDetailVO;
import io.yak.ops.common.bean.vo.observability.SqlExecutionAuditSummaryVO;
import io.yak.ops.common.bean.vo.observability.SqlExecutionAuditVO;
import io.yak.ops.common.bean.vo.observability.SqlStatementExecutionAuditVO;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import io.yak.ops.core.execution.sql.SqlTransactionMode;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
public class SqlExecutionAuditConverter {
  public SqlExecutionAuditCriteria criteria(SqlExecutionAuditQueryDTO dto) {
    SqlExecutionAuditQueryDTO source = dto == null ? new SqlExecutionAuditQueryDTO() : dto;
    return new SqlExecutionAuditCriteria(source.getPageNo() == null ? 1 : source.getPageNo(), source.getPageSize() == null ? 20 : source.getPageSize(), source.getExecutionId(), source.getDataSourceId(), parseEnum(SqlExecutionCaller.class, source.getCaller(), "caller"), source.getCallerReference(), source.getOperatorName(), parseEnum(SqlExecutionStatus.class, source.getStatus(), "status"), parseEnum(SqlTransactionMode.class, source.getTransactionMode(), "transactionMode"), parseEnum(SqlStatementType.class, source.getStatementType(), "statementType"), source.getSqlFingerprint(), source.getMinDurationMs(), source.getStartedFrom(), source.getStartedTo());
  }
  public PagingData<SqlExecutionAuditVO> page(PageData<SqlExecutionAuditRecord> page) { return PagingData.from(page.map(this::execution)); }
  public SqlExecutionAuditDetailVO detail(SqlExecutionAuditDetail detail) { return new SqlExecutionAuditDetailVO(execution(detail.execution()), detail.statements().stream().map(this::statement).toList()); }
  public SqlExecutionAuditSummaryVO summary(SqlExecutionAuditSummary summary) {
    return new SqlExecutionAuditSummaryVO(summary.total(), summary.succeeded(), summary.failed(), summary.cancelled(), summary.timedOut(), summary.successRate(), summary.avgDurationMs(), summary.maxDurationMs(), summary.p95DurationMs(), summary.returnedRows(), summary.affectedRows(), summary.statementTypes().stream().map(value -> new SqlExecutionAuditSummaryVO.StatementTypeCountVO(value.statementType() == null ? "OTHER" : value.statementType().name(), value.count())).toList());
  }
  private SqlExecutionAuditVO execution(SqlExecutionAuditRecord row) { return new SqlExecutionAuditVO(row.executionId(), row.dataSourceId(), name(row.caller()), row.callerReference(), row.operatorName(), name(row.transactionMode()), name(row.status()), row.statementCount(), row.succeededStatementCount(), row.returnedRows(), row.affectedRows(), row.startedAt(), row.finishedAt(), row.durationMs(), row.errorMessage()); }
  private SqlStatementExecutionAuditVO statement(SqlStatementAuditRecord row) { return new SqlStatementExecutionAuditVO(row.statementId(), row.statementIndex(), name(row.statementType()), row.sqlFingerprint(), row.sqlPreview(), name(row.status()), row.resultType(), row.returnedRows(), row.affectedRows(), row.truncated(), row.startedAt(), row.finishedAt(), row.durationMs(), row.errorMessage()); }
  private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String fieldName) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    try { return Enum.valueOf(enumType, normalized); }
    catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid " + fieldName + ": " + value, exception); }
  }
  private static String name(Enum<?> value) { return value == null ? null : value.name(); }
}
