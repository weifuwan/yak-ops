package io.yak.ops.business.datasource.execution.audit;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.execution.sql.SqlExecutionObserver;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionSnapshot;
import io.yak.ops.core.execution.sql.SqlFingerprint;
import io.yak.ops.core.execution.sql.SqlStatementSnapshot;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.dao.entity.datasource.SqlExecutionAuditEntity;
import io.yak.ops.dao.entity.datasource.SqlStatementExecutionAuditEntity;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 异步持久化已完成的 SQL execution 审计元数据，不保存结果行和绑定参数。
 *
 * @author weifuwan
 * @since 2026-08-18
 */
@Component
@ConditionalOnDataSourceEnabled
public final class PersistentSqlExecutionObserver implements SqlExecutionObserver {

    private static final Logger log = LoggerFactory.getLogger(PersistentSqlExecutionObserver.class);
    private static final int QUEUE_CAPACITY = 2048;
    private static final int SQL_PREVIEW_LIMIT = 2048;

    @Resource
    private SqlExecutionAuditStore store;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2,
            2,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            Thread.ofPlatform().daemon(true).name("yak-sql-audit-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    @Override
    public void onExecutionCompleted(SqlExecutionSnapshot snapshot) {
        if (snapshot == null || !snapshot.terminal()) return;
        AuditBatch batch = map(snapshot);
        try {
            executor.execute(() -> persist(batch));
        } catch (RejectedExecutionException exception) {
            log.warn("SQL audit queue is full; dropping execution audit: executionId={}", snapshot.executionId());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void persist(AuditBatch batch) {
        try {
            store.save(batch.execution(), batch.statements());
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to persist SQL execution audit: executionId={}",
                    batch.execution().getExecutionId(),
                    exception);
        }
    }

    static AuditBatch map(SqlExecutionSnapshot snapshot) {
        SqlExecutionAuditEntity execution = new SqlExecutionAuditEntity();
        execution.setExecutionId(snapshot.executionId());
        execution.setDataSourceId(snapshot.dataSourceId());
        execution.setCaller(enumName(snapshot.context().caller()));
        execution.setCallerReference(snapshot.context().callerReference());
        execution.setOperatorName(snapshot.context().operator());
        execution.setTransactionMode(enumName(snapshot.transactionMode()));
        execution.setStatus(enumName(snapshot.status()));
        execution.setStatementCount(snapshot.statements().size());
        execution.setSucceededStatementCount((int) snapshot.statements().stream()
                .filter(statement -> statement.status() == SqlStatementStatus.SUCCEEDED)
                .count());
        execution.setReturnedRows(snapshot.statements().stream()
                .map(SqlStatementSnapshot::result)
                .filter(result -> result != null)
                .mapToLong(SqlExecutionResult::returnedRows)
                .sum());
        execution.setAffectedRows(snapshot.statements().stream()
                .map(SqlStatementSnapshot::result)
                .filter(result -> result != null)
                .mapToLong(SqlExecutionResult::affectedRows)
                .sum());
        execution.setStartedAt(local(snapshot.startedAt()));
        execution.setFinishedAt(local(snapshot.finishedAt()));
        execution.setDurationMs(snapshot.durationMillis());
        execution.setErrorMessage(limit(snapshot.errorMessage(), 1000));

        List<SqlStatementExecutionAuditEntity> statements =
                new ArrayList<>(snapshot.statements().size());
        for (SqlStatementSnapshot statement : snapshot.statements()) {
            SqlStatementExecutionAuditEntity row = new SqlStatementExecutionAuditEntity();
            row.setExecutionId(snapshot.executionId());
            row.setStatementId(statement.statementId());
            row.setStatementIndex(statement.index());
            row.setStatementType(enumName(statement.statementType()));
            row.setSqlFingerprint(SqlFingerprint.sha256(statement.sql()));
            row.setSqlPreview(SqlFingerprint.redactedPreview(statement.sql(), SQL_PREVIEW_LIMIT));
            row.setStatus(enumName(statement.status()));
            SqlExecutionResult result = statement.result();
            row.setResultType(result == null ? null : enumName(result.type()));
            row.setReturnedRows(result == null ? 0L : (long) result.returnedRows());
            row.setAffectedRows(result == null ? 0L : result.affectedRows());
            row.setTruncated(result != null && result.truncated());
            row.setStartedAt(local(statement.startedAt()));
            row.setFinishedAt(local(statement.finishedAt() == null ? snapshot.finishedAt() : statement.finishedAt()));
            row.setDurationMs(statement.durationMillis());
            row.setErrorMessage(limit(statement.errorMessage(), 1000));
            statements.add(row);
        }
        return new AuditBatch(execution, List.copyOf(statements));
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static LocalDateTime local(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }

    private static String limit(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) return value;
        return value.substring(0, maxChars);
    }

    record AuditBatch(SqlExecutionAuditEntity execution, List<SqlStatementExecutionAuditEntity> statements) {}
}
