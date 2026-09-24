package io.yak.ops.business.datasource.execution;

import jakarta.annotation.Resource;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.execution.domain.SqlExecutionAggregate;
import io.yak.ops.business.datasource.gateway.SqlExecutionGateway;
import io.yak.ops.business.datasource.gateway.SqlExecutionGateway.Command;
import io.yak.ops.business.datasource.gateway.SqlExecutionGateway.Session;
import io.yak.ops.core.execution.sql.LexicalSqlStatementClassifier;
import io.yak.ops.core.execution.sql.SqlExecutionColumn;
import io.yak.ops.core.execution.sql.SqlExecutionPlan;
import io.yak.ops.core.execution.sql.SqlExecutionPolicy;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionResultType;
import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import io.yak.ops.core.execution.sql.SqlExecutionSnapshot;
import io.yak.ops.core.execution.sql.SqlExecutionTiming;
import io.yak.ops.core.execution.sql.SqlStatementClassification;
import io.yak.ops.core.execution.sql.SqlStatementClassifier;
import io.yak.ops.core.execution.sql.SqlStatementRequest;
import io.yak.ops.core.execution.sql.SqlTransactionMode;
import jakarta.annotation.PreDestroy;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** SQL runtime orchestration backed by a Business SQL execution Gateway. */
@Component
@ConditionalOnDataSourceEnabled
public final class DefaultSqlExecutionRuntime implements SqlExecutionRuntime {

    private static final int MAX_COMPLETED_EXECUTIONS = 512;

    @Resource
    private SqlExecutionGateway executionGateway;

    @Resource
    private SqlExecutionPolicy executionPolicy;

    private final SqlStatementClassifier statementClassifier = new LexicalSqlStatementClassifier();
    private final ExecutorService lifecycleExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, RuntimeExecution> executions = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> completedOrder = new ConcurrentLinkedDeque<>();

    @Override
    public SqlExecutionResult execute(SqlExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        classifyAndValidate(request.context(), request.sql());
        return executeFresh(request, null, null);
    }

    @Override
    public SqlExecutionSnapshot start(SqlExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        List<SqlStatementClassification> classifications =
                new ArrayList<>(plan.statements().size());
        for (SqlStatementRequest statement : plan.statements()) {
            classifications.add(classifyAndValidate(plan.context(), statement.sql()));
        }

        String executionId = "sql-" + UUID.randomUUID();
        SqlExecutionAggregate aggregate = new SqlExecutionAggregate(executionId, plan, classifications);
        RuntimeExecution execution = new RuntimeExecution(aggregate);
        executions.put(executionId, execution);
        try {
            lifecycleExecutor.submit(() -> run(execution));
        } catch (RuntimeException exception) {
            execution.failBeforeStart(exception);
            retainCompleted(executionId);
        }
        return execution.snapshot();
    }

    @Override
    public Optional<SqlExecutionSnapshot> find(String executionId) {
        if (executionId == null || executionId.isBlank()) return Optional.empty();
        RuntimeExecution execution = executions.get(executionId.trim());
        return execution == null ? Optional.empty() : Optional.of(execution.snapshot());
    }

    @Override
    public SqlExecutionSnapshot await(String executionId) {
        RuntimeExecution execution = requireExecution(executionId);
        try {
            return execution.completion().join();
        } catch (RuntimeException exception) {
            return execution.snapshot();
        }
    }

    @Override
    public boolean cancel(String executionId) {
        RuntimeExecution execution = executions.get(normalizeExecutionId(executionId));
        if (execution == null || !execution.requestCancel()) return false;
        Session active = execution.activeSession().get();
        if (active != null) cancelSafely(active);
        return true;
    }

    @PreDestroy
    void shutdown() {
        lifecycleExecutor.shutdownNow();
    }

    private SqlStatementClassification classifyAndValidate(
            io.yak.ops.core.execution.sql.SqlExecutionContext context, String sql) {
        SqlStatementClassification classification = statementClassifier.classify(sql);
        executionPolicy.validate(context, classification);
        return classification;
    }

    private void run(RuntimeExecution execution) {
        execution.markRunning();
        try {
            if (execution.plan().transactionMode() == SqlTransactionMode.SINGLE_TRANSACTION) {
                runSingleTransaction(execution);
            } else {
                runAutoCommit(execution);
            }
        } catch (RuntimeException exception) {
            execution.finishUnexpectedFailure(safeMessage(exception));
        } finally {
            if (!execution.snapshot().terminal()) {
                execution.finishUnexpectedFailure("SQL execution ended without a terminal state");
            }
            retainCompleted(execution.executionId());
        }
    }

    private void runAutoCommit(RuntimeExecution execution) {
        List<SqlStatementRequest> statements = execution.plan().statements();
        for (int index = 0; index < statements.size(); index++) {
            if (execution.cancelRequested()) {
                execution.finishCancelled(index, "SQL execution cancelled");
                return;
            }

            SqlStatementRequest statement = statements.get(index);
            execution.markStatementRunning(index);
            SqlExecutionRequest request = request(execution, statement);
            try {
                SqlExecutionResult result = executeFresh(request, execution.activeSession(), execution);
                execution.markStatementSucceeded(index, result);
            } catch (RuntimeException exception) {
                finishStatementException(execution, index, exception);
                return;
            }
        }

        if (execution.cancelRequested()) {
            execution.finishCancelled(statements.size(), "SQL execution cancelled");
        } else {
            execution.finishSucceeded();
        }
    }

    private void runSingleTransaction(RuntimeExecution execution) {
        if (execution.cancelRequested()) {
            execution.finishCancelled(0, "SQL execution cancelled");
            return;
        }

        Session session = null;
        boolean transactionStarted = false;
        try {
            session = executionGateway.open(execution.plan().dataSourceId());
            execution.activeSession().set(session);

            if (execution.cancelRequested()) {
                cancelSafely(session);
                execution.finishCancelled(0, "SQL execution cancelled");
                return;
            }
            if (!session.supportsTransactions()) {
                execution.finishFailed(0, "Datasource SQL executor does not support SINGLE_TRANSACTION");
                return;
            }

            session.beginTransaction();
            transactionStarted = true;
            if (execution.cancelRequested()) {
                String rollbackError = rollbackSafely(session);
                transactionStarted = false;
                execution.finishCancelled(0, appendRollbackError("SQL execution cancelled", rollbackError));
                return;
            }

            List<SqlStatementRequest> statements = execution.plan().statements();
            for (int index = 0; index < statements.size(); index++) {
                if (execution.cancelRequested()) {
                    String rollbackError = rollbackSafely(session);
                    transactionStarted = false;
                    execution.finishCancelled(index, appendRollbackError("SQL execution cancelled", rollbackError));
                    return;
                }

                SqlStatementRequest statement = statements.get(index);
                execution.markStatementRunning(index);
                SqlExecutionRequest request = request(execution, statement);
                try {
                    SqlExecutionResult result = executeExisting(request, session);
                    execution.markStatementSucceeded(index, result);
                } catch (RuntimeException exception) {
                    String rollbackError = rollbackSafely(session);
                    transactionStarted = false;
                    finishStatementException(execution, index, exception, rollbackError);
                    return;
                }
            }

            if (execution.cancelRequested()) {
                String rollbackError = rollbackSafely(session);
                transactionStarted = false;
                execution.finishCancelled(
                        statements.size(), appendRollbackError("SQL execution cancelled", rollbackError));
                return;
            }

            try {
                session.commitTransaction();
                transactionStarted = false;
                execution.finishSucceeded();
            } catch (RuntimeException exception) {
                String rollbackError = rollbackSafely(session);
                transactionStarted = false;
                execution.finishFailed(statements.size(), appendRollbackError(safeMessage(exception), rollbackError));
            }
        } catch (RuntimeException exception) {
            String rollbackError = transactionStarted && session != null ? rollbackSafely(session) : null;
            if (execution.cancelRequested()) {
                execution.finishCancelled(0, appendRollbackError("SQL execution cancelled", rollbackError));
            } else {
                execution.finishFailed(0, appendRollbackError(safeMessage(exception), rollbackError));
            }
        } finally {
            execution.activeSession().compareAndSet(session, null);
            closeSafely(session);
        }
    }

    private SqlExecutionRequest request(RuntimeExecution execution, SqlStatementRequest statement) {
        return new SqlExecutionRequest(
                execution.plan().dataSourceId(),
                statement.sql(),
                statement.parameters(),
                statement.maxRows(),
                statement.timeoutSeconds(),
                execution.plan().context());
    }

    private void finishStatementException(RuntimeExecution execution, int index, RuntimeException exception) {
        finishStatementException(execution, index, exception, null);
    }

    private void finishStatementException(
            RuntimeExecution execution, int index, RuntimeException exception, String rollbackError) {
        String message = appendRollbackError(safeMessage(exception), rollbackError);
        if (execution.cancelRequested()) {
            execution.markStatementCancelled(index, message);
            execution.finishCancelled(index + 1, message);
        } else if (causedBy(exception, SQLTimeoutException.class)) {
            execution.markStatementTimedOut(index, message);
            execution.finishTimedOut(index + 1, message);
        } else {
            execution.markStatementFailed(index, message);
            execution.finishFailed(index + 1, message);
        }
    }

    private SqlExecutionResult executeFresh(
            SqlExecutionRequest request, AtomicReference<Session> activeSession, RuntimeExecution runtimeExecution) {
        long totalStartedAt = System.nanoTime();
        long openStartedAt = System.nanoTime();
        Session session = executionGateway.open(request.dataSourceId());
        long openMillis = elapsedMillis(openStartedAt);
        if (activeSession != null) activeSession.set(session);

        try (session) {
            if (runtimeExecution != null && runtimeExecution.cancelRequested()) {
                cancelSafely(session);
                throw new IllegalStateException("SQL execution cancelled");
            }
            return executeOnSession(request, session, openMillis, totalStartedAt);
        } finally {
            if (activeSession != null) activeSession.compareAndSet(session, null);
        }
    }

    private SqlExecutionResult executeExisting(SqlExecutionRequest request, Session session) {
        long totalStartedAt = System.nanoTime();
        return executeOnSession(request, session, 0L, totalStartedAt);
    }

    private SqlExecutionResult executeOnSession(
            SqlExecutionRequest request, Session session, long openMillis, long totalStartedAt) {
        long executeStartedAt = System.nanoTime();
        SqlExecutionGateway.Result result = session.execute(
                new Command(request.sql(), request.parameters(), request.maxRows(), request.timeoutSeconds()));
        long executeMillis = elapsedMillis(executeStartedAt);
        long totalMillis = elapsedMillis(totalStartedAt);

        return new SqlExecutionResult(
                result.resultSet() ? SqlExecutionResultType.RESULT_SET : SqlExecutionResultType.UPDATE_COUNT,
                mapColumns(result.columns()),
                result.rows(),
                result.affectedRows(),
                result.truncated(),
                new SqlExecutionTiming(openMillis, executeMillis, totalMillis));
    }

    private static void cancelSafely(Session session) {
        if (session == null) return;
        try {
            session.cancel();
        } catch (RuntimeException ignored) {
            // Best effort cancellation.
        }
    }

    private String rollbackSafely(Session session) {
        try {
            session.rollbackTransaction();
            return null;
        } catch (RuntimeException exception) {
            return safeMessage(exception);
        }
    }

    private static void closeSafely(Session session) {
        if (session == null) return;
        try {
            session.close();
        } catch (RuntimeException ignored) {
            // Execution outcome is already reflected in the lifecycle aggregate.
        }
    }

    private static String appendRollbackError(String message, String rollbackError) {
        if (rollbackError == null || rollbackError.isBlank()) return message;
        return message + "; rollback failed: " + rollbackError;
    }

    private RuntimeExecution requireExecution(String executionId) {
        String normalized = normalizeExecutionId(executionId);
        RuntimeExecution execution = executions.get(normalized);
        if (execution == null) {
            throw new IllegalArgumentException("SQL execution not found: " + normalized);
        }
        return execution;
    }

    private String normalizeExecutionId(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        return executionId.trim();
    }

    private void retainCompleted(String executionId) {
        completedOrder.addLast(executionId);
        while (completedOrder.size() > MAX_COMPLETED_EXECUTIONS) {
            String expired = completedOrder.pollFirst();
            if (expired != null) executions.remove(expired);
        }
    }

    private static List<SqlExecutionColumn> mapColumns(List<SqlExecutionGateway.Column> columns) {
        return columns.stream()
                .map(column -> new SqlExecutionColumn(
                        column.name(), column.label(), column.typeName(), column.jdbcType(), column.nullable()))
                .toList();
    }

    private static boolean causedBy(Throwable throwable, Class<? extends Throwable> expected) {
        Throwable current = throwable;
        while (current != null) {
            if (expected.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable == null
                    ? "SQL execution failed"
                    : throwable.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static final class RuntimeExecution {

        private final SqlExecutionAggregate aggregate;
        private final AtomicReference<Session> activeSession = new AtomicReference<>();
        private final CompletableFuture<SqlExecutionSnapshot> completion = new CompletableFuture<>();

        private RuntimeExecution(SqlExecutionAggregate aggregate) {
            this.aggregate = aggregate;
        }

        String executionId() {
            return aggregate.executionId();
        }

        SqlExecutionPlan plan() {
            return aggregate.plan();
        }

        AtomicReference<Session> activeSession() {
            return activeSession;
        }

        CompletableFuture<SqlExecutionSnapshot> completion() {
            return completion;
        }

        boolean cancelRequested() {
            return aggregate.cancelRequested();
        }

        boolean requestCancel() {
            return aggregate.requestCancel();
        }

        void markRunning() {
            aggregate.markRunning();
        }

        void markStatementRunning(int index) {
            aggregate.markStatementRunning(index);
        }

        void markStatementSucceeded(int index, SqlExecutionResult result) {
            aggregate.markStatementSucceeded(index, result);
        }

        void markStatementFailed(int index, String message) {
            aggregate.markStatementFailed(index, message);
        }

        void markStatementTimedOut(int index, String message) {
            aggregate.markStatementTimedOut(index, message);
        }

        void markStatementCancelled(int index, String message) {
            aggregate.markStatementCancelled(index, message);
        }

        void finishSucceeded() {
            aggregate.finishSucceeded();
            completeIfTerminal();
        }

        void finishFailed(int skipFrom, String message) {
            aggregate.finishFailed(skipFrom, message);
            completeIfTerminal();
        }

        void finishTimedOut(int skipFrom, String message) {
            aggregate.finishTimedOut(skipFrom, message);
            completeIfTerminal();
        }

        void finishCancelled(int skipFrom, String message) {
            aggregate.finishCancelled(skipFrom, message);
            completeIfTerminal();
        }

        void finishUnexpectedFailure(String message) {
            aggregate.finishUnexpectedFailure(message);
            completeIfTerminal();
        }

        void failBeforeStart(Throwable throwable) {
            aggregate.failBeforeStart(safeMessage(throwable));
            completeIfTerminal();
        }

        SqlExecutionSnapshot snapshot() {
            return aggregate.snapshot();
        }

        private void completeIfTerminal() {
            SqlExecutionSnapshot snapshot = aggregate.snapshot();
            if (snapshot.terminal()) completion.complete(snapshot);
        }
    }
}
