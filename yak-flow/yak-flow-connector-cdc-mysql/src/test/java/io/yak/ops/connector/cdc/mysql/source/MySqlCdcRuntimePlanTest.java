package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.sink.JdbcSink;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.graph.StreamingJobGraphGenerator;
import io.yak.ops.flow.runtime.jobgraph.JobGraph;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Validates the CDC-to-JDBC physical graph without starting Debezium or JDBC connections.
 *
 * <p>The Hybrid Source is parallel, but the JDBC UPSERT Writer is deliberately single-task
 * until ordered UPDATE_BEFORE / UPDATE_AFTER routing is supported across Sink subtasks.
 */
class MySqlCdcRuntimePlanTest {

    @TempDir
    Path checkpoints;

    @Test
    void compilesParallelHybridSourceToOneOrderedMultiTableJdbcSink() {
        Configuration configuration = configuration();
        StreamGraph graph = graph(configuration);
        JobGraph job = new StreamingJobGraphGenerator(graph, configuration).generate();

        assertEquals(RuntimeExecutionMode.STREAMING, job.runtimeMode());
        assertFalse(job.isBounded());
        assertEquals(2, job.getVertices().size());
        assertEquals(1, job.getEdges().size());
        assertEquals(2, graph.getSourceNodes().getFirst().getParallelism());
        assertEquals(1, graph.getSinkNodes().getFirst().getParallelism());
    }

    @Test
    void refusesHybridPipelineWithoutDurableCheckpointDirectory() {
        Configuration configuration = configuration();
        StreamGraph graph = graph(configuration);
        configuration.removeConfig(CheckpointingOptions.STATE_DIRECTORY);
        assertThrows(IllegalArgumentException.class,
                () -> new StreamingJobGraphGenerator(graph, configuration).generate());
    }

    private Configuration configuration() {
        return new Configuration()
                .set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(10))
                .set(CheckpointingOptions.STATE_DIRECTORY, checkpoints.toString())
                .set(PipelineOptions.AUTO_GENERATE_UIDS, false);
    }

    private static StreamGraph graph(Configuration configuration) {
        TableSchema schema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.BIGINT.copy(false)),
                        new Column("NAME", LogicalTypes.varchar(64))),
                List.of("ID"));
        TableId sourceA = new TableId("sample", null, "orders");
        TableId sourceB = new TableId("sample", null, "items");
        MySqlHybridCdcSource source = MySqlCdcSource.builder()
                .hostname("unused")
                .username("replication")
                .password("not-used")
                .serverId(5571)
                .topicPrefix("yak_cdc_runtime_plan")
                .table(sourceA, schema)
                .table(sourceB, schema)
                .buildHybrid(1024);
        JdbcSink sink = JdbcSink.builder()
                .withConnectionOptions(new JdbcConnectionOptions(
                        "jdbc:postgresql://127.0.0.1:5432/sample", "unused", "not-used"))
                .withTablePlans(List.of(
                        new JdbcTableWritePlan(
                                sourceA, new TableId(null, "public", "orders"), schema, JdbcWriteMode.UPSERT),
                        new JdbcTableWritePlan(
                                sourceB, new TableId(null, "public", "items"), schema, JdbcWriteMode.UPSERT)))
                .build();
        SourceTransformation<TableRecord> input =
                new SourceTransformation<>("mysql-cdc", source, TableRecord.class, 2);
        SinkTransformation<TableRecord> output =
                new SinkTransformation<>(input, "jdbc-upsert", sink, 1);
        input.setUid("mysql-cdc-runtime-plan-source");
        output.setUid("mysql-cdc-runtime-plan-sink");
        return new StreamGraphGenerator(output, configuration).generate();
    }
}
