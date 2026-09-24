package io.yak.ops.business.datasource.execution.audit;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.PageData;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import io.yak.ops.core.execution.sql.SqlTransactionMode;
import io.yak.ops.dao.entity.datasource.SqlExecutionAuditEntity;
import io.yak.ops.dao.entity.datasource.SqlStatementExecutionAuditEntity;
import io.yak.ops.dao.model.datasource.SqlExecutionAuditSummaryRow;
import io.yak.ops.dao.repository.datasource.SqlExecutionAuditRepository;
import io.yak.ops.dao.repository.datasource.SqlExecutionAuditRepository.Query;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 读取 SQL execution 审计数据，并将 DAO 持久化结果转换为业务查询模型。
 *
 * @author weifuwan
 * @since 2026-08-24
 */
@Component
@ConditionalOnDataSourceEnabled
public class SqlExecutionAuditReader {

    @Resource
    private SqlExecutionAuditRepository auditRepository;

    public PageData<SqlExecutionAuditRecord> page(SqlExecutionAuditCriteria criteria) {
        return auditRepository.queryPage(toQuery(criteria)).map(this::executionRecord);
    }

    public SqlExecutionAuditDetail detail(String executionId) {
        if (executionId == null || executionId.isBlank())
            throw new IllegalArgumentException("executionId must not be blank");
        String normalizedId = executionId.trim();
        SqlExecutionAuditEntity execution = auditRepository.queryByExecutionId(normalizedId);
        if (execution == null)
            throw new IllegalArgumentException("SQL execution audit not found: " + normalizedId);
        List<SqlStatementAuditRecord> statements = auditRepository.queryStatements(normalizedId).stream()
                .map(this::statementRecord)
                .toList();
        return new SqlExecutionAuditDetail(executionRecord(execution), statements);
    }

    public SqlExecutionAuditSummary summary(SqlExecutionAuditCriteria criteria) {
        Query query = toQuery(criteria);
        SqlExecutionAuditSummaryRow summary = auditRepository.querySummary(query);
        long p95 = auditRepository.queryP95DurationMs(query);
        List<SqlExecutionAuditSummary.StatementTypeCount> statementTypes =
                auditRepository.queryStatementTypeCounts(query).stream()
                        .map(row -> new SqlExecutionAuditSummary.StatementTypeCount(
                                enumValue(SqlStatementType.class, row.getStatementType()), row.getCount()))
                        .toList();
        double successRate =
                summary.getTotal() == 0L ? 0D : summary.getSucceeded() / (double) summary.getTotal();
        return new SqlExecutionAuditSummary(
                summary.getTotal(),
                summary.getSucceeded(),
                summary.getFailed(),
                summary.getCancelled(),
                summary.getTimedOut(),
                successRate,
                summary.getAvgDurationMs(),
                summary.getMaxDurationMs(),
                p95,
                summary.getReturnedRows(),
                summary.getAffectedRows(),
                statementTypes);
    }

    private Query toQuery(SqlExecutionAuditCriteria criteria) {
        SqlExecutionAuditCriteria value = criteria == null
                ? new SqlExecutionAuditCriteria(
                        1, 20, null, null, null, null, null, null, null, null, null, null, null, null)
                : criteria;
        return new Query(
                value.pageNo(),
                value.pageSize(),
                value.executionId(),
                value.dataSourceId(),
                enumName(value.caller()),
                value.callerReference(),
                value.operatorName(),
                enumName(value.status()),
                enumName(value.transactionMode()),
                enumName(value.statementType()),
                value.sqlFingerprint(),
                value.minDurationMs(),
                value.startedFrom(),
                value.startedTo());
    }

    private SqlExecutionAuditRecord executionRecord(SqlExecutionAuditEntity row) {
        return new SqlExecutionAuditRecord(
                row.getExecutionId(),
                row.getDataSourceId(),
                enumValue(SqlExecutionCaller.class, row.getCaller()),
                row.getCallerReference(),
                row.getOperatorName(),
                enumValue(SqlTransactionMode.class, row.getTransactionMode()),
                enumValue(SqlExecutionStatus.class, row.getStatus()),
                value(row.getStatementCount()),
                value(row.getSucceededStatementCount()),
                value(row.getReturnedRows()),
                value(row.getAffectedRows()),
                row.getStartedAt(),
                row.getFinishedAt(),
                value(row.getDurationMs()),
                row.getErrorMessage());
    }

    private SqlStatementAuditRecord statementRecord(SqlStatementExecutionAuditEntity row) {
        return new SqlStatementAuditRecord(
                row.getStatementId(),
                value(row.getStatementIndex()),
                enumValue(SqlStatementType.class, row.getStatementType()),
                row.getSqlFingerprint(),
                row.getSqlPreview(),
                enumValue(SqlStatementStatus.class, row.getStatus()),
                row.getResultType(),
                value(row.getReturnedRows()),
                value(row.getAffectedRows()),
                Boolean.TRUE.equals(row.getTruncated()),
                row.getStartedAt(),
                row.getFinishedAt(),
                value(row.getDurationMs()),
                row.getErrorMessage());
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        return Enum.valueOf(type, value);
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }
}
