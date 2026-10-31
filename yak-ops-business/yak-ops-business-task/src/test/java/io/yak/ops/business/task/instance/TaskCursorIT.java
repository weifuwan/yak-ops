package io.yak.ops.business.task.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.dao.repository.task.TaskCursorBatchRow;
import io.yak.ops.dao.repository.task.TaskCursorRepository;
import io.yak.ops.dao.repository.task.impl.TaskCursorRepositoryImpl;
import io.yak.ops.plugin.task.api.execution.TaskCursorBatch;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.testutils.TestNames;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Real database transactions for cursor freeze, rollback and optimistic commit, without Boot. */
@Tag("business")
@Timeout(120)
class TaskCursorIT {
    @RegisterExtension
    static final MySqlDatabase MYSQL = new MySqlDatabase();

    private final String name = TestNames.unique("cursor");
    private AnnotationConfigApplicationContext context;
    private TaskCursorStoreService store;
    private TaskCursorRepository repository;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private boolean created;
    private static final String KEY = "a".repeat(64);
    private static final String OTHER = "b".repeat(64);
    private static final String BINDING = "c".repeat(64);

    @BeforeEach
    void createDatabaseAndTransactionalServices() throws Exception {
        DatabaseMetadata db = MYSQL.metadata();
        db.execute("CREATE DATABASE " + name);
        created = true;
        DataSource source = new DriverManagerDataSource(
                "jdbc:mysql://" + db.host() + ":" + db.port() + "/" + name
                        + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=30000",
                db.username(),
                db.password());
        Flyway.configure().dataSource(source).locations("classpath:db/migration/yak-ops").load().migrate();
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> source);
        context.register(Beans.class, TaskCursorRepositoryImpl.class, TaskCursorStoreService.class);
        context.refresh();
        store = context.getBean(TaskCursorStoreService.class);
        repository = context.getBean(TaskCursorRepository.class);
        jdbc = context.getBean(JdbcTemplate.class);
        transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterEach
    void dropDatabase() throws Exception {
        if (context != null) context.close();
        if (created) MYSQL.metadata().execute("DROP DATABASE " + name);
    }

    @Test
    void invalidMultiTableFreezeRollsBackEveryInsertedRow() {
        var proposal = proposal(KEY, "10");
        assertThrows(
                IllegalArgumentException.class, () -> store.freeze(identity("first"), List.of(proposal, proposal)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM yak_ops_task_cursor", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM yak_ops_task_cursor_batch", Integer.class));
    }

    @Test
    void failedWindowIsReusedUntilItsCandidateCommits() {
        var initial =
                store.freeze(identity("first"), List.of(proposal(KEY, "10"))).getFirst();
        var retry =
                store.freeze(identity("retry"), List.of(proposal(KEY, "20"))).getFirst();
        assertEquals(initial.upper(), retry.upper());
        assertEquals("10", retry.upper());
        transactions.executeWithoutResult(status -> repository.commit("ws", "task", row(retry)));
        var next = store.freeze(identity("next"), List.of(proposal(KEY, "20"))).getFirst();
        assertEquals("10", next.lower());
        assertEquals("20", next.upper());
        assertEquals(1, next.expectedVersion());
        assertFalse(next.initialLoad());
    }

    @Test
    void staleSecondTableRollsBackTheFirstTablesCommit() {
        var batches = store.freeze(identity("first"), List.of(proposal(KEY, "10"), proposal(OTHER, "20")));
        TaskCursorBatch second = batches.get(1);
        var stale = new TaskCursorBatchRow(
                second.tableKey(),
                second.binding(),
                second.mode(),
                second.column(),
                second.initialValue(),
                second.lower(),
                second.upper(),
                second.allowInitialLoad(),
                second.initialLoad(),
                99);
        assertThrows(
                IllegalStateException.class,
                () -> transactions.executeWithoutResult(status -> {
                    repository.commit("ws", "task", row(batches.getFirst()));
                    repository.commit("ws", "task", stale);
                }));
        assertEquals(0, jdbc.queryForObject("SELECT SUM(revision) FROM yak_ops_task_cursor", Integer.class));
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM yak_ops_task_cursor WHERE cursor_value IS NULL AND pending_value IS NOT NULL",
                        Integer.class));
    }

    @Test
    void concurrentCommitsHaveExactlyOneRevisionWinner() throws Exception {
        var batch = row(
                store.freeze(identity("first"), List.of(proposal(KEY, "10"))).getFirst());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Commit start barrier timed out");
                try {
                    transactions.executeWithoutResult(status -> repository.commit("ws", "task", batch));
                    return true;
                } catch (IllegalStateException stale) {
                    return false;
                }
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("Commit workers did not start");
            start.countDown();
            assertEquals(1, (first.get(15, TimeUnit.SECONDS) ? 1 : 0) + (second.get(15, TimeUnit.SECONDS) ? 1 : 0));
        }
        assertEquals(1, jdbc.queryForObject("SELECT revision FROM yak_ops_task_cursor", Integer.class));
    }

    @Test
    void anotherWorkspaceCannotInheritFrozenProgress() {
        store.freeze(identity("first"), List.of(proposal(KEY, "10")));
        var other = store.freeze(new TaskExecutionIdentity("other", "task", 1, "second"), List.of(proposal(KEY, "20")))
                .getFirst();
        assertEquals("20", other.upper());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM yak_ops_task_cursor", Integer.class));
    }

    @Test
    void changedSemanticBindingInNewDefinitionVersionRollsBackEveryStagedWindow() {
        List<TaskCursorBatch> initial =
                store.freeze(identity("initial"), List.of(proposal(KEY, "10"), proposal(OTHER, "20")));
        transactions.executeWithoutResult(status -> initial.forEach(batch -> repository.commit("ws", "task", row(batch))));

        TaskExecutionIdentity unchangedVersion = new TaskExecutionIdentity("ws", "task", 2, "unchanged-version");
        List<TaskCursorBatch> continued =
                store.freeze(unchangedVersion, List.of(proposal(KEY, "30"), proposal(OTHER, "40")));
        assertEquals(List.of("10", "20"), continued.stream().map(TaskCursorBatch::lower).toList());
        assertFalse(continued.stream().anyMatch(TaskCursorBatch::initialLoad));
        transactions.executeWithoutResult(status -> continued.forEach(batch -> repository.commit("ws", "task", row(batch))));

        TaskExecutionIdentity changedVersion = new TaskExecutionIdentity("ws", "task", 3, "changed-version");
        TaskCursorBatch changed = TaskCursorBatch.proposal(OTHER, "d".repeat(64), "INCREASING", "id", "0", "60", true);
        assertThrows(IllegalStateException.class,
                () -> store.freeze(changedVersion, List.of(proposal(KEY, "50"), changed)));
        assertEquals(List.of("30", "40"), jdbc.queryForList(
                "SELECT cursor_value FROM yak_ops_task_cursor ORDER BY table_key", String.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_task_cursor WHERE pending_value IS NOT NULL", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_task_cursor_batch WHERE instance_id = 'changed-version'", Integer.class));
        assertEquals(List.of(BINDING, BINDING), jdbc.queryForList(
                "SELECT binding FROM yak_ops_task_cursor ORDER BY table_key", String.class));
    }

    private static TaskExecutionIdentity identity(String instance) {
        return new TaskExecutionIdentity("ws", "task", 1, instance);
    }

    private static TaskCursorBatch proposal(String key, String upper) {
        return TaskCursorBatch.proposal(key, BINDING, "INCREASING", "id", "0", upper, true);
    }

    private static TaskCursorBatchRow row(TaskCursorBatch r) {
        return new TaskCursorBatchRow(
                r.tableKey(),
                r.binding(),
                r.mode(),
                r.column(),
                r.initialValue(),
                r.lower(),
                r.upper(),
                r.allowInitialLoad(),
                r.initialLoad(),
                r.expectedVersion());
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Beans {
        @Bean
        JdbcTemplate jdbc(DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new JdbcTransactionManager(source);
        }
    }
}
