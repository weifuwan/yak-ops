package io.yak.ops.modeling.ddl.doris.execution;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Waits for Doris asynchronous column schema-change jobs to reach a terminal state. */
@Component
public class SchemaChangeWaiter {

    private static final Logger log = LoggerFactory.getLogger(DorisSchemaChangeWaiter.class);
    private static final long TIMEOUT_MILLIS = 10L * 60L * 1000L;
    private static final long NO_JOB_GRACE_MILLIS = 3000L;
    private static final long POLL_INTERVAL_MILLIS = 1000L;

    public Long latestJobId(Connection connection, String database, String tableName) throws SQLException {
        JobStatus status = queryLatestJob(connection, database, tableName);
        if (status == null) {
            return null;
        }
        String state = status.state == null ? "" : status.state.toUpperCase(Locale.ROOT);
        if ("PENDING".equals(state) || "WAITING_TXN".equals(state) || "RUNNING".equals(state)) {
            throw new IllegalStateException(
                    "Doris 表已有 Schema Change 任务正在执行，请等待完成后再发布: " + tableName);
        }
        return status.jobId;
    }

    public void awaitCompletion(
            Connection connection, String database, String tableName, Long previousJobId)
            throws SQLException, InterruptedException {
        long startedAt = System.currentTimeMillis();
        long deadline = startedAt + TIMEOUT_MILLIS;
        Long observedJobId = null;
        long noJobDeadline = startedAt + NO_JOB_GRACE_MILLIS;

        while (System.currentTimeMillis() < deadline) {
            JobStatus job = queryLatestJob(connection, database, tableName);
            if (job == null || (previousJobId != null && previousJobId.equals(job.jobId))) {
                if (System.currentTimeMillis() >= noJobDeadline) {
                    // Doris light schema change may finish synchronously without creating a new job.
                    return;
                }
                sleep();
                continue;
            }

            if (!Objects.equals(job.jobId, previousJobId)
                    && !Objects.equals(job.jobId, observedJobId)) {
                observedJobId = job.jobId;
                log.info(
                        "Doris Schema Change submitted: database={}, table={}, jobId={}, state={}",
                        database, tableName, job.jobId, job.state);
            }
            String state = job.state == null ? "" : job.state.toUpperCase(Locale.ROOT);
            if ("FINISHED".equals(state)) {
                log.info(
                        "Doris Schema Change finished: database={}, table={}, jobId={}",
                        database, tableName, job.jobId);
                return;
            }
            if ("CANCELLED".equals(state)) {
                log.warn(
                        "Doris Schema Change cancelled: database={}, table={}, jobId={}, message={}",
                        database, tableName, job.jobId, job.message);
                throw new IllegalStateException(
                        "Doris Schema Change 执行失败: " + job.message);
            }
            if (!"PENDING".equals(state)
                    && !"WAITING_TXN".equals(state)
                    && !"RUNNING".equals(state)) {
                throw new IllegalStateException(
                        "Doris Schema Change 返回未知状态: " + job.state);
            }
            sleep();
        }

        throw new IllegalStateException(
                "等待 Doris Schema Change 超时（10 分钟），请在 Doris 执行 SHOW ALTER TABLE COLUMN 检查任务状态。");
    }

    private JobStatus queryLatestJob(
            Connection connection, String database, String tableName) throws SQLException {
        String safeTableName = tableName.replace("'", "''");
        String safeDatabase = "`" + database.replace("`", "``") + "`";
        String sql = "SHOW ALTER TABLE COLUMN FROM " + safeDatabase
                + " WHERE TableName = '" + safeTableName
                + "' ORDER BY CreateTime DESC LIMIT 1";
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) {
                return null;
            }
            long jobIdValue = result.getLong("JobId");
            Long jobId = result.wasNull() ? null : jobIdValue;
            return new JobStatus(
                    jobId,
                    result.getString("State"),
                    result.getString("Msg"));
        }
    }

    private void sleep() throws InterruptedException {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        }
    }

    private static final class JobStatus {
        private final Long jobId;
        private final String state;
        private final String message;

        private JobStatus(Long jobId, String state, String message) {
            this.jobId = jobId;
            this.state = state;
            this.message = message;
        }
    }
}
