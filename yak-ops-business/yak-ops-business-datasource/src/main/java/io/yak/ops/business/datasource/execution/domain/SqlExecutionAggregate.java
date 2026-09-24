package io.yak.ops.business.datasource.execution.domain;

import io.yak.ops.core.execution.sql.SqlExecutionPlan;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionSnapshot;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlStatementClassification;
import io.yak.ops.core.execution.sql.SqlStatementSnapshot;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SQL execution lifecycle aggregate.
 *
 * <p>This object owns execution/statement state transitions only. Threads, futures, physical
 * datasource sessions and cancellation I/O remain runtime concerns.
 */
public final class SqlExecutionAggregate {

    private final String executionId;
    private final SqlExecutionPlan plan;
    private final List<StatementState> statements;

    private boolean cancelRequested;
    private SqlExecutionStatus status = SqlExecutionStatus.PENDING;
    private Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;

    public SqlExecutionAggregate(
            String executionId, SqlExecutionPlan plan, List<SqlStatementClassification> classifications) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        this.executionId = executionId.trim();
        this.plan = Objects.requireNonNull(plan, "plan");
        List<SqlStatementClassification> values = classifications == null ? List.of() : List.copyOf(classifications);
        if (values.size() != plan.statements().size()) {
            throw new IllegalArgumentException("statement classifications must match execution plan");
        }
        this.statements = new ArrayList<>(plan.statements().size());
        for (int index = 0; index < plan.statements().size(); index++) {
            SqlStatementClassification classification =
                    Objects.requireNonNull(values.get(index), "statement classification");
            statements.add(new StatementState(
                    this.executionId + ":stmt:" + (index + 1),
                    index,
                    plan.statements().get(index).sql(),
                    classification.primaryType()));
        }
    }

    public String executionId() {
        return executionId;
    }

    public SqlExecutionPlan plan() {
        return plan;
    }

    public synchronized boolean cancelRequested() {
        return cancelRequested;
    }

    public synchronized boolean requestCancel() {
        if (status.terminal()) return false;
        cancelRequested = true;
        status = SqlExecutionStatus.CANCELLING;
        return true;
    }

    public synchronized void markRunning() {
        if (status.terminal()) return;
        if (startedAt == null) startedAt = Instant.now();
        status = cancelRequested ? SqlExecutionStatus.CANCELLING : SqlExecutionStatus.RUNNING;
    }

    public synchronized void markStatementRunning(int index) {
        StatementState statement = statement(index);
        statement.status = SqlStatementStatus.RUNNING;
        statement.startedAt = Instant.now();
    }

    public synchronized void markStatementSucceeded(int index, SqlExecutionResult result) {
        StatementState statement = statement(index);
        statement.result = Objects.requireNonNull(result, "result");
        statement.status = SqlStatementStatus.SUCCEEDED;
        statement.finishedAt = Instant.now();
    }

    public synchronized void markStatementFailed(int index, String message) {
        finishStatement(index, SqlStatementStatus.FAILED, message);
    }

    public synchronized void markStatementTimedOut(int index, String message) {
        finishStatement(index, SqlStatementStatus.TIMED_OUT, message);
    }

    public synchronized void markStatementCancelled(int index, String message) {
        finishStatement(index, SqlStatementStatus.CANCELLED, message);
    }

    public synchronized void finishSucceeded() {
        complete(SqlExecutionStatus.SUCCEEDED, null, statements.size());
    }

    public synchronized void finishFailed(int skipFrom, String message) {
        complete(SqlExecutionStatus.FAILED, message, skipFrom);
    }

    public synchronized void finishTimedOut(int skipFrom, String message) {
        complete(SqlExecutionStatus.TIMED_OUT, message, skipFrom);
    }

    public synchronized void finishCancelled(int skipFrom, String message) {
        complete(SqlExecutionStatus.CANCELLED, message, skipFrom);
    }

    public synchronized void finishUnexpectedFailure(String message) {
        if (status.terminal()) return;
        int skipFrom = 0;
        for (int index = 0; index < statements.size(); index++) {
            StatementState statement = statements.get(index);
            if (statement.status == SqlStatementStatus.RUNNING) {
                statement.status = SqlStatementStatus.FAILED;
                statement.errorMessage = message;
                statement.finishedAt = Instant.now();
                skipFrom = index + 1;
                break;
            }
            if (statement.status == SqlStatementStatus.SUCCEEDED) {
                skipFrom = index + 1;
                continue;
            }
            skipFrom = index;
            break;
        }
        complete(SqlExecutionStatus.FAILED, message, skipFrom);
    }

    public synchronized void failBeforeStart(String message) {
        if (startedAt == null) startedAt = Instant.now();
        complete(SqlExecutionStatus.FAILED, message, 0);
    }

    public synchronized boolean terminal() {
        return status.terminal();
    }

    public synchronized SqlExecutionSnapshot snapshot() {
        List<SqlStatementSnapshot> snapshots =
                statements.stream().map(StatementState::snapshot).toList();
        return new SqlExecutionSnapshot(
                executionId,
                status,
                plan.dataSourceId(),
                plan.context(),
                plan.transactionMode(),
                snapshots,
                startedAt,
                finishedAt,
                errorMessage);
    }

    private void finishStatement(int index, SqlStatementStatus statementStatus, String message) {
        StatementState statement = statement(index);
        statement.status = statementStatus;
        statement.errorMessage = message;
        statement.finishedAt = Instant.now();
    }

    private StatementState statement(int index) {
        if (index < 0 || index >= statements.size()) {
            throw new IllegalArgumentException("statement index out of range: " + index);
        }
        return statements.get(index);
    }

    private void complete(SqlExecutionStatus finalStatus, String message, int skipFrom) {
        if (status.terminal()) return;
        if (!finalStatus.terminal()) {
            throw new IllegalArgumentException("final execution status must be terminal");
        }
        Instant now = Instant.now();
        for (int index = Math.max(0, skipFrom); index < statements.size(); index++) {
            StatementState statement = statements.get(index);
            if (statement.status == SqlStatementStatus.PENDING) {
                statement.status = SqlStatementStatus.SKIPPED;
                statement.finishedAt = now;
                statement.errorMessage = message;
            }
        }
        status = finalStatus;
        errorMessage = message;
        finishedAt = now;
    }

    private static final class StatementState {

        private final String statementId;
        private final int index;
        private final String sql;
        private final SqlStatementType statementType;
        private SqlStatementStatus status = SqlStatementStatus.PENDING;
        private SqlExecutionResult result;
        private String errorMessage;
        private Instant startedAt;
        private Instant finishedAt;

        private StatementState(String statementId, int index, String sql, SqlStatementType statementType) {
            this.statementId = statementId;
            this.index = index;
            this.sql = sql;
            this.statementType = statementType;
        }

        private SqlStatementSnapshot snapshot() {
            return new SqlStatementSnapshot(
                    statementId, index, sql, statementType, status, result, errorMessage, startedAt, finishedAt);
        }
    }
}
