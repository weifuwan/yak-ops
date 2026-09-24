package io.yak.ops.business.datasource.execution;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.execution.sql.LexicalSqlStatementClassifier;
import io.yak.ops.core.execution.sql.SqlExecutionObserver;
import io.yak.ops.core.execution.sql.SqlExecutionPlan;
import io.yak.ops.core.execution.sql.SqlExecutionPolicyViolationException;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import io.yak.ops.core.execution.sql.SqlExecutionSnapshot;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlStatementSnapshot;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import io.yak.ops.core.execution.sql.SqlTransactionMode;
import jakarta.annotation.PreDestroy;
import java.sql.SQLTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Primary SQL runtime facade that adds terminal observability without coupling persistence to the
 * physical execution runtime.
 */
@Component
@Primary
@ConditionalOnDataSourceEnabled
public final class ObservableSqlExecutionRuntime implements SqlExecutionRuntime {

  private static final Logger log = LoggerFactory.getLogger(ObservableSqlExecutionRuntime.class);

  private final DefaultSqlExecutionRuntime delegate;
  private final List<SqlExecutionObserver> observers;
  private final LexicalSqlStatementClassifier classifier = new LexicalSqlStatementClassifier();
  private final ExecutorService tracker = Executors.newVirtualThreadPerTaskExecutor();

  public ObservableSqlExecutionRuntime(
      DefaultSqlExecutionRuntime delegate,
      ObjectProvider<SqlExecutionObserver> observers) {
    this(delegate, observers.orderedStream().toList());
  }

  ObservableSqlExecutionRuntime(
      DefaultSqlExecutionRuntime delegate,
      List<SqlExecutionObserver> observers) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.observers = observers == null ? List.of() : List.copyOf(observers);
  }

  @Override
  public SqlExecutionResult execute(SqlExecutionRequest request) {
    Objects.requireNonNull(request, "request");
    SqlStatementType statementType = classifier.classify(request.sql()).primaryType();
    String executionId = "sql-sync-" + UUID.randomUUID();
    String statementId = executionId + ":stmt:1";
    Instant startedAt = Instant.now();
    try {
      SqlExecutionResult result = delegate.execute(request);
      Instant finishedAt = Instant.now();
      notifyObservers(new SqlExecutionSnapshot(
          executionId,
          SqlExecutionStatus.SUCCEEDED,
          request.dataSourceId(),
          request.context(),
          SqlTransactionMode.AUTO_COMMIT,
          List.of(new SqlStatementSnapshot(
              statementId,
              0,
              request.sql(),
              statementType,
              SqlStatementStatus.SUCCEEDED,
              result,
              null,
              startedAt,
              finishedAt)),
          startedAt,
          finishedAt,
          null));
      return result;
    } catch (SqlExecutionPolicyViolationException exception) {
      // Rejected SQL never reached a datasource and is not treated as a physical execution audit.
      throw exception;
    } catch (RuntimeException exception) {
      Instant finishedAt = Instant.now();
      boolean timedOut = causedBy(exception, SQLTimeoutException.class);
      SqlExecutionStatus executionStatus =
          timedOut ? SqlExecutionStatus.TIMED_OUT : SqlExecutionStatus.FAILED;
      SqlStatementStatus statementStatus =
          timedOut ? SqlStatementStatus.TIMED_OUT : SqlStatementStatus.FAILED;
      String message = safeMessage(exception);
      notifyObservers(new SqlExecutionSnapshot(
          executionId,
          executionStatus,
          request.dataSourceId(),
          request.context(),
          SqlTransactionMode.AUTO_COMMIT,
          List.of(new SqlStatementSnapshot(
              statementId,
              0,
              request.sql(),
              statementType,
              statementStatus,
              null,
              message,
              startedAt,
              finishedAt)),
          startedAt,
          finishedAt,
          message));
      throw exception;
    }
  }

  @Override
  public SqlExecutionSnapshot start(SqlExecutionPlan plan) {
    SqlExecutionSnapshot started = delegate.start(plan);
    if (started.terminal()) {
      notifyObservers(started);
      return started;
    }
    try {
      tracker.submit(() -> notifyObservers(delegate.await(started.executionId())));
    } catch (RuntimeException exception) {
      // Observability scheduling must not change the underlying execution lifecycle.
      log.warn(
          "Unable to schedule SQL execution observability: executionId={}",
          started.executionId(),
          exception);
    }
    return started;
  }

  @Override
  public Optional<SqlExecutionSnapshot> find(String executionId) {
    return delegate.find(executionId);
  }

  @Override
  public SqlExecutionSnapshot await(String executionId) {
    return delegate.await(executionId);
  }

  @Override
  public boolean cancel(String executionId) {
    return delegate.cancel(executionId);
  }

  @PreDestroy
  void shutdown() {
    tracker.shutdownNow();
  }

  private void notifyObservers(SqlExecutionSnapshot snapshot) {
    if (snapshot == null || !snapshot.terminal()) return;
    for (SqlExecutionObserver observer : observers) {
      try {
        observer.onExecutionCompleted(snapshot);
      } catch (RuntimeException exception) {
        log.warn(
            "SQL execution observer failed: executionId={}, observer={}",
            snapshot.executionId(),
            observer.getClass().getName(),
            exception);
      }
    }
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
      return throwable == null ? "SQL execution failed" : throwable.getClass().getSimpleName();
    }
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }
}
