package io.yak.ops.connector.cdc.mysql.integration;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSource;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.connector.jdbc.xa.MySqlXaSink;
import io.yak.ops.connector.jdbc.xa.MySqlXaXid;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.connector.sink.Committer;
import io.yak.ops.core.api.connector.sink.CommittingSinkWriter;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.SupportsCommitter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.flow.runtime.execution.EmbeddedJobClient;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Isolated JVM with the real CDC Source and XA Sink, controlled only by test files. */
public final class MySqlCdcXaCrashRecoveryProcess {

    private MySqlCdcXaCrashRecoveryProcess() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 13) throw new IllegalArgumentException("Expected 13 process arguments");
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String sourceTable = args[2];
        String targetUrl = args[3];
        String targetTable = args[4];
        Path state = Path.of(args[5]);
        Path control = Path.of(args[6]);
        int generation = Integer.parseInt(args[7]);
        boolean restore = Boolean.parseBoolean(args[8]);
        int serverId = Integer.parseInt(args[9]);
        String topic = args[10];
        String instanceId = args[11];
        Fault fault = Fault.valueOf(args[12]);
        Files.createDirectories(state);
        Files.createDirectories(control);

        TableSchema schema = new TableSchema(
                List.of(
                        new Column("bucket", LogicalTypes.BIGINT.copy(false)),
                        new Column("amount", LogicalTypes.decimal(14, 2))),
                List.of());
        TableId from = new TableId("yak_cdc_it", null, sourceTable);
        TableId to;
        try (var connection = DriverManager.getConnection(
                targetUrl, required("YAK_XA_TARGET_USER"), required("YAK_XA_TARGET_PASSWORD"))) {
            to = new TableId(connection.getCatalog(), null, targetTable);
        }

        TableMetrics metrics = new TableMetrics();
        var source = MySqlCdcSource.builder()
                .hostname(host)
                .port(port)
                .username(required("YAK_XA_SOURCE_USER"))
                .password(required("YAK_XA_SOURCE_PASSWORD"))
                .serverId(serverId)
                .topicPrefix(topic)
                .table(from, schema, "bucket")
                .buildHybrid(1, metrics);
        var connection = new JdbcConnectionOptions(
                targetUrl, required("YAK_XA_TARGET_USER"), required("YAK_XA_TARGET_PASSWORD"));
        var delegate = new MySqlXaSink(
                connection,
                new MySqlJdbcDialect(),
                new JdbcTableWritePlan(from, to, schema, JdbcWriteMode.RETRACT),
                instanceId);
        var sink = new ControlledSink(delegate, control, generation, fault);

        Configuration configuration = new Configuration()
                .set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(30))
                .set(CheckpointingOptions.STATE_DIRECTORY, state.toString())
                .set(CheckpointingOptions.RESTORE_LATEST, restore);
        SourceTransformation<TableRecord> input =
                new SourceTransformation<>("xa-crash-cdc", source, TableRecord.class, 1);
        SinkTransformation<TableRecord> output =
                new SinkTransformation<>(input, "xa-crash-sink", sink, 1);
        input.setUid("xa-crash-cdc-source");
        output.setUid("xa-crash-mysql-sink");
        var graph = new StreamGraphGenerator(output, configuration).generate();
        JobClient job = new EmbeddedPipelineExecutor().execute(graph, configuration).get(30, TimeUnit.SECONDS);

        awaitStreaming(job, metrics);
        Files.writeString(control.resolve("ready-" + generation), "STREAMING");
        while (true) {
            if (job.getJobStatus().get(5, TimeUnit.SECONDS).isTerminalState()) {
                throw new IllegalStateException("XA CDC child stopped unexpectedly");
            }
            try (Stream<Path> files = Files.list(control)) {
                for (Path request : files.filter(p -> p.getFileName().toString().endsWith(".request"))
                        .sorted()
                        .toList()) {
                    String name = request.getFileName().toString();
                    Files.delete(request);
                    long id = ((EmbeddedJobClient) job).checkpoint().get(60, TimeUnit.SECONDS).checkpointId();
                    Files.writeString(control.resolve(name.replace(".request", ".ack")), Long.toString(id));
                }
            }
            Thread.sleep(50);
        }
    }

    private static void awaitStreaming(JobClient job, TableMetrics metrics) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            if (job.getJobStatus().get(5, TimeUnit.SECONDS).isTerminalState()) {
                throw new IllegalStateException("XA CDC failed before Binlog handoff");
            }
            if (metrics.continuousSnapshot().phase() == TableMetrics.Phase.STREAMING) return;
            Thread.sleep(100);
        }
        throw new IllegalStateException("XA CDC never reached STREAMING");
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + key);
        return value;
    }

    enum Fault {
        NONE,
        AFTER_PREPARE,
        BEFORE_COMMIT,
        AFTER_COMMIT
    }

    /** Test-only interception preserves the real Connector and Runtime contracts. */
    private record ControlledSink(MySqlXaSink actual, Path control, int generation, Fault fault)
            implements SupportsCommitter<TableRecord, MySqlXaXid> {

        @Override
        public CommittingSinkWriter<TableRecord, MySqlXaXid> createWriter(WriterInitContext context)
                throws Exception {
            CommittingSinkWriter<TableRecord, MySqlXaXid> writer = actual.createWriter(context);
            return new CommittingSinkWriter<>() {
                @Override
                public void write(TableRecord row, SinkWriter.Context metadata) throws Exception {
                    writer.write(row, metadata);
                    if (row.rowKind() == RowKind.INSERT
                            && ((Number) row.row().getField(0)).longValue() == 2L) {
                        Files.writeString(control.resolve("delivered-" + generation), "2");
                    }
                }

                @Override
                public void flush(boolean endOfInput) throws Exception {
                    writer.flush(endOfInput);
                }

                @Override
                public Collection<MySqlXaXid> prepareCommit(long checkpointId) throws Exception {
                    Collection<MySqlXaXid> prepared = writer.prepareCommit(checkpointId);
                    if (!prepared.isEmpty() && fault == Fault.AFTER_PREPARE && armed()) {
                        pause("after-prepare");
                    }
                    return prepared;
                }

                @Override
                public void close() throws Exception {
                    writer.close();
                }
            };
        }

        @Override
        public Committer<MySqlXaXid> createCommitter(WriterInitContext context) throws Exception {
            Committer<MySqlXaXid> committer = actual.createCommitter(context);
            return new Committer<>() {
                @Override
                public void commit(Collection<MySqlXaXid> committables, boolean recovered) throws Exception {
                    if (!recovered && !committables.isEmpty() && armed() && fault == Fault.BEFORE_COMMIT) {
                        pause("before-commit");
                    }
                    committer.commit(committables, recovered);
                    if (!recovered && !committables.isEmpty() && armed() && fault == Fault.AFTER_COMMIT) {
                        pause("after-commit");
                    }
                }

                @Override
                public void recover(Collection<MySqlXaXid> keep) throws Exception {
                    committer.recover(keep);
                }

                @Override
                public void close() throws Exception {
                    committer.close();
                }
            };
        }

        @Override
        public SimpleVersionedSerializer<MySqlXaXid> getCommittableSerializer() {
            return actual.getCommittableSerializer();
        }

        private boolean armed() {
            return Files.exists(control.resolve("armed"));
        }

        private void pause(String window) throws Exception {
            Files.writeString(control.resolve("fault-" + window), window);
            // The parent verifies database and disk state, then kills this JVM.
            while (true) Thread.sleep(100);
        }
    }
}
