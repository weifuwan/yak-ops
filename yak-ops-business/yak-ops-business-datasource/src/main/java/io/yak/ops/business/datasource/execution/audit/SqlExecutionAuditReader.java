package io.yak.ops.business.datasource.execution.audit;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.dao.SqlExecutionAuditDao;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditPO;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditQuery;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditSummaryRow;
import io.yak.ops.business.datasource.dao.model.SqlStatementExecutionAuditPO;
import io.yak.ops.common.PageData;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** SQL execution observability read-side role. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class SqlExecutionAuditReader {

    private final SqlExecutionAuditDao auditDao;

    public PageData<SqlExecutionAuditRecord> page(SqlExecutionAuditCriteria criteria) {
        SqlExecutionAuditQuery query = toQuery(criteria);
        IPage<SqlExecutionAuditPO> page = auditDao.selectPage(query);
        return new PageData<>(
                page.getRecords().stream().map(this::executionRecord).toList(),
                page.getTotal(),
                page.getPages(),
                page.getCurrent(),
                page.getSize());
    }

    public SqlExecutionAuditDetail detail(String executionId) {
        if (executionId == null || executionId.isBlank())
            throw new IllegalArgumentException("executionId must not be blank");
        String normalizedId = executionId.trim();
        SqlExecutionAuditPO execution = auditDao.selectByExecutionId(normalizedId);
        if (execution == null) throw new IllegalArgumentException("SQL execution audit not found: " + normalizedId);
        List<SqlStatementAuditRecord> statements = auditDao.selectStatements(normalizedId).stream()
                .map(this::statementRecord)
                .toList();
        return new SqlExecutionAuditDetail(executionRecord(execution), statements);
    }

    public SqlExecutionAuditSummary summary(SqlExecutionAuditCriteria criteria) {
        SqlExecutionAuditQuery query = toQuery(criteria);
        SqlExecutionAuditSummaryRow summary = auditDao.selectSummary(query);
        long p95 = auditDao.selectP95DurationMs(query);
        List<SqlExecutionAuditSummary.StatementTypeCount> statementTypes =
                auditDao.selectStatementTypeCounts(query).stream()
                        .map(row ->
                                new SqlExecutionAuditSummary.StatementTypeCount(row.getStatementType(), row.getCount()))
                        .toList();
        double successRate = summary.getTotal() == 0L ? 0D : summary.getSucceeded() / (double) summary.getTotal();
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

    private SqlExecutionAuditQuery toQuery(SqlExecutionAuditCriteria criteria) {
        SqlExecutionAuditCriteria value = criteria == null
                ? new SqlExecutionAuditCriteria(
                        1, 20, null, null, null, null, null, null, null, null, null, null, null, null)
                : criteria;
        return new SqlExecutionAuditQuery(
                value.pageNo(),
                value.pageSize(),
                value.executionId(),
                value.dataSourceId(),
                value.caller(),
                value.callerReference(),
                value.operatorName(),
                value.status(),
                value.transactionMode(),
                value.statementType(),
                value.sqlFingerprint(),
                value.minDurationMs(),
                value.startedFrom(),
                value.startedTo());
    }

    private SqlExecutionAuditRecord executionRecord(SqlExecutionAuditPO row) {
        return new SqlExecutionAuditRecord(
                row.getExecutionId(),
                row.getDataSourceId(),
                row.getCaller(),
                row.getCallerReference(),
                row.getOperatorName(),
                row.getTransactionMode(),
                row.getStatus(),
                value(row.getStatementCount()),
                value(row.getSucceededStatementCount()),
                value(row.getReturnedRows()),
                value(row.getAffectedRows()),
                row.getStartedAt(),
                row.getFinishedAt(),
                value(row.getDurationMs()),
                row.getErrorMessage());
    }

    private SqlStatementAuditRecord statementRecord(SqlStatementExecutionAuditPO row) {
        return new SqlStatementAuditRecord(
                row.getStatementId(),
                value(row.getStatementIndex()),
                row.getStatementType(),
                row.getSqlFingerprint(),
                row.getSqlPreview(),
                row.getStatus(),
                row.getResultType() == null ? null : row.getResultType().name(),
                value(row.getReturnedRows()),
                value(row.getAffectedRows()),
                Boolean.TRUE.equals(row.getTruncated()),
                row.getStartedAt(),
                row.getFinishedAt(),
                value(row.getDurationMs()),
                row.getErrorMessage());
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }
}
