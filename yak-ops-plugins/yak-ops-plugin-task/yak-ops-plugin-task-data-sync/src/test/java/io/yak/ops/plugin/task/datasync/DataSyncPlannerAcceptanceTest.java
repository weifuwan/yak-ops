package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceOptions;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.config.JobConfig;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** V4 task parameters reach the physical Planner without relaxing component admission. */
class DataSyncPlannerAcceptanceTest {
    private static final TableSchema FULL = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(32)),
                    new Column("updated_at", LogicalTypes.timestamp(3))),
            List.of("id"));
    private static final TableSchema PARTIAL = new TableSchema(
            List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
    private static final TaskExecutionIdentity IDENTITY =
            new TaskExecutionIdentity("ws", "definition", 1, "instance-acceptance");

    private final DataSyncTaskPlugin plugin = DataSyncTaskPluginContractTest.plugin();

    @Test
    void incrementalSourceConfigurationSurvivesDefinitionAndPlanner() {
        ReadonlyConfig parsed = plugin.parse(offlineIncremental());
        JobConfig job = JobConfig.from(parsed);
        assertEquals("updated_at", job.tables().getFirst().incremental().column());
        assertEquals(5, job.tables().getFirst().incremental().lookbackSeconds());
        assertFalse(job.source().getSourceMap().containsKey("incremental"));
        assertFalse(job.sink().getSourceMap().containsKey("incremental"));
        assertEquals("UPSERT", job.sink().get(JdbcSinkOptions.WRITE_MODE).name());

        var resources = new Metadata();
        var planned = new JobPlanner(resources, new FactoryUtil()).plan(parsed, IDENTITY);
        assertEquals("orders", planned.source().table().table());
        assertEquals("ods_orders", planned.tables().getFirst().targetTable().table());
        assertEquals(1, planned.admissionPlan().exclusiveResources().size());
        assertEquals(0, resources.connectionReads);
    }

    @Test
    void schemaPreparationRetainsAdmissionWhileTargetTransitionsToExpectedState() {
        var resources = new Metadata();
        resources.targetSchema = PARTIAL;
        JobPlanner planner = new JobPlanner(resources, new FactoryUtil());
        ReadonlyConfig parameters = plugin.parse(offlineWithSchemaPreparation());
        var before = planner.plan(parameters, IDENTITY);
        assertTrue(before.tables().getFirst().schemaPlan().requiresDdl());

        // The task's own DDL changes the physical target; admission must stay stable.
        resources.targetSchema = FULL;
        var after = planner.plan(parameters, IDENTITY);
        assertFalse(after.tables().getFirst().schemaPlan().requiresDdl());
        assertEquals(before.admissionPlan(), after.admissionPlan());
        assertEquals(before.admissionPlan().exclusiveResources(), after.admissionPlan().exclusiveResources());
        assertEquals(0, resources.connectionReads);
    }

    @Test
    void realtimeOptionsAreScopedToTheSourceAndItsInstance() {
        var resources = new Metadata();
        ReadonlyConfig parameters = plugin.parse(realtime());
        var plan = new JobPlanner(resources, new FactoryUtil()).plan(parameters, IDENTITY);
        var route = plan.tables().getFirst();
        assertEquals("MYSQL_CDC", route.sourceParameters().getSourceMap().get("type"));
        assertEquals("JDBC", route.sinkParameters().getSourceMap().get("type"));
        assertTrue(route.sourceOptions().contains(MySqlCdcSourceOptions.SERVER_ID));
        assertTrue(route.sourceOptions().contains(MySqlCdcSourceOptions.TOPIC_PREFIX));
        assertFalse(route.sinkParameters().contains(MySqlCdcSourceOptions.SERVER_ID));
        assertFalse(route.sinkParameters().contains(JdbcSinkOptions.XA_INSTANCE_ID));
        assertEquals(2, plan.admissionPlan().exclusiveResources().size());
        assertEquals(0, resources.connectionReads);
    }

    @Test
    void keylessRetractWithoutSinkCheckpointTransactionFailsAdmission() {
        String invalid = realtime()
                .replace("\"id\":\"orders\",\"table\":\"orders\"",
                        "\"id\":\"orders\",\"table\":\"orders\",\"keyless\":true,\"snapshot\":{\"chunkKeyColumn\":\"id\"}")
                .replace("\"mode\":\"UPSERT\"", "\"mode\":\"RETRACT\"");
        assertThrows(TaskPluginException.class, () -> plugin.parse(invalid));
    }

    private static String offlineIncremental() {
        return """
                {"solutionType":"SINGLE_TABLE_OFFLINE","steps":["FULL","INCREMENTAL"],
                 "source":{"datasourceId":"source","tables":[{"id":"orders","table":"orders",
                           "incremental":{"mode":"TIMESTAMP","column":"updated_at",
                                          "initialValue":"2026-10-01T00:00:00",
                                          "delaySeconds":0,"lookbackSeconds":5}}]},
                 "transform":[],
                 "sink":{"datasourceId":"target",
                         "schemaPolicy":{"initial":"VERIFY","evolution":{"enabled":false,"allowDropColumns":false}},
                         "tables":[{"input":"orders","database":"ods","schema":"public","table":"ods_orders",
                                    "write":{"mode":"UPSERT"}}]},
                 "env":{}}
                """;
    }

    private static String offlineWithSchemaPreparation() {
        return """
                {"solutionType":"SINGLE_TABLE_OFFLINE","steps":["FULL"],
                 "source":{"datasourceId":"source","tables":[{"id":"orders","table":"orders"}]},
                 "transform":[],
                 "sink":{"datasourceId":"target",
                         "schemaPolicy":{"initial":"CREATE_OR_ALTER",
                                         "evolution":{"enabled":false,"allowDropColumns":false}},
                         "tables":[{"input":"orders","database":"ods","schema":"public","table":"ods_orders",
                                    "write":{"mode":"APPEND"}}]},
                 "env":{}}
                """;
    }

    private static String realtime() {
        return """
                {"solutionType":"SINGLE_TABLE_REALTIME","steps":["FULL","INCREMENTAL"],
                 "source":{"datasourceId":"source","tables":[{"id":"orders","table":"orders"}]},
                 "transform":[],
                 "sink":{"datasourceId":"target",
                         "schemaPolicy":{"initial":"VERIFY","evolution":{"enabled":false,"allowDropColumns":false}},
                         "tables":[{"input":"orders","database":"ods","schema":"public","table":"ods_orders",
                                    "write":{"mode":"UPSERT"}}]},
                 "env":{}}
                """;
    }

    private static final class Metadata implements DataSourceLookupService {
        private TableSchema targetSchema = FULL;
        private int connectionReads;

        @Override
        public DataSourceInfo dataSource(String workspace, String id) {
            assertEquals("ws", workspace);
            if ("source".equals(id)) return new DataSourceInfo("MYSQL", "app", null, 1);
            if ("target".equals(id)) return new DataSourceInfo("POSTGRE_SQL", "ods", "public", 1);
            throw new AssertionError("Unknown datasource");
        }

        @Override
        public DataSourceTable table(String workspace, String id, String database, String schema, String name) {
            assertEquals("ws", workspace);
            if ("source".equals(id)) {
                return new DataSourceTable("MYSQL", new TableId("app", null, "orders"), FULL, 1);
            }
            if ("target".equals(id)) {
                return new DataSourceTable(
                        "POSTGRE_SQL", new TableId("ods", "public", "ods_orders"), targetSchema, 1);
            }
            throw new AssertionError("Unknown datasource");
        }

        @Override
        public Optional<DataSourceTable> findTable(
                String workspace, String id, String database, String schema, String name) {
            return Optional.of(table(workspace, id, database, schema, name));
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspace, String id) {
            connectionReads++;
            throw new AssertionError("Planner must not load credentials");
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspace, String id, long revision) {
            connectionReads++;
            throw new AssertionError("Planner must not load credentials");
        }
    }
}
