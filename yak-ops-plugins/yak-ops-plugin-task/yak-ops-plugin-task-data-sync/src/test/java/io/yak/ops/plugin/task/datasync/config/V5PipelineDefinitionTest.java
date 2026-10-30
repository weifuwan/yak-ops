package io.yak.ops.plugin.task.datasync.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.plugin.task.datasync.solution.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V5 is the only persisted pipeline format; JobConfig is an internal execution projection. */
class V5PipelineDefinitionTest {
    private static final String V5 = """
            {
              "source": {
                "type": "MYSQL_CDC", "datasourceId": "mysql",
                "startup": {"mode": "INITIAL"},
                "tables": [{"database": "app", "table": "orders"}]
              },
              "sink": {
                "type": "JDBC", "datasourceId": "postgres",
                "schemaPolicy": {
                  "initial": "VERIFY",
                  "evolution": {"enabled": false, "allowDropColumns": false}
                },
                "tables": [{"schema": "public", "table": "ods_orders", "write": {"mode": "UPSERT"}}]
              },
              "route": [{"source-table": "app.orders", "sink-table": "public.ods_orders"}],
              "transform": [],
              "pipeline": {"execution.runtime-mode": "STREAMING"}
            }
            """;

    @Test
    void readsNativeV5PipelineWithoutLegacyConversion() {
        JobConfig plan = JobConfig.from(ConfigParser.parseJson(V5));
        assertEquals(List.of(Step.FULL, Step.INCREMENTAL), plan.steps());
        assertEquals(1, plan.tables().size());
        assertEquals("orders", plan.tables().getFirst().source().get(JobOptions.TABLE));
        assertEquals("ods_orders", plan.tables().getFirst().sink().get(JobOptions.TABLE));
    }

    @Test
    void preservesDatabaseScopeWithOneRoute() {
        String database = V5.replace("\"execution.runtime-mode\": \"STREAMING\"",
                "\"execution.runtime-mode\": \"STREAMING\", \"pipeline.table-scope\": \"DATABASE\"");
        JobConfig plan = JobConfig.from(ConfigParser.parseJson(database));
        assertEquals(io.yak.ops.plugin.task.datasync.solution.SolutionType.DATABASE_REALTIME, plan.solutionType());
    }

    @Test
    void acceptsChunkKeyPerTableForMultiTableCdc() {
        String multi = V5.replace("\"tables\": [{\"database\": \"app\", \"table\": \"orders\"}]",
                "\"tables\": [{\"database\": \"app\", \"table\": \"orders\","
                        + " \"snapshot\": {\"chunkKeyColumn\": \"bucket\"}},"
                        + " {\"database\": \"app\", \"table\": \"users\","
                        + " \"snapshot\": {\"chunkKeyColumn\": \"id\"}}]")
                .replace("\"schema\": \"public\", \"table\": \"ods_orders\", \"write\": {\"mode\": \"UPSERT\"}",
                        "\"schema\": \"public\", \"table\": \"ods_orders\", \"write\": {\"mode\": \"UPSERT\"}},"
                        + " {\"schema\": \"public\", \"table\": \"ods_users\", \"write\": {\"mode\": \"UPSERT\"}")
                .replace("{\"source-table\": \"app.orders\", \"sink-table\": \"public.ods_orders\"}",
                        "{\"source-table\": \"app.orders\", \"sink-table\": \"public.ods_orders\"},"
                        + " {\"source-table\": \"app.users\", \"sink-table\": \"public.ods_users\"}");
        JobConfig config = JobConfig.from(ConfigParser.parseJson(multi));
        assertEquals(2, config.tables().size());
        assertEquals(io.yak.ops.plugin.task.datasync.solution.SolutionType.DATABASE_REALTIME, config.solutionType());
    }

    @Test
    void databaseScopeWithSingleRouteCannotUseRetract() {
        String database = V5.replace("\"execution.runtime-mode\": \"STREAMING\"",
                        "\"execution.runtime-mode\": \"STREAMING\", \"pipeline.table-scope\": \"DATABASE\"")
                .replace("\"write\": {\"mode\": \"UPSERT\"}",
                        "\"write\": {\"mode\": \"RETRACT\", \"transaction\": {\"mode\": \"CHECKPOINT_XA\"}}");
        assertThrows(IllegalArgumentException.class, () -> JobConfig.from(ConfigParser.parseJson(database)));
    }

    @Test
    void rejectsV4Fields() {
        assertThrows(IllegalArgumentException.class, () ->
                JobConfig.from(ConfigParser.parseJson(
                        "{\"solutionType\":\"SINGLE_TABLE_REALTIME\",\"steps\":[\"FULL\"]}")));
    }

    @Test
    void checksRuntimeMode() {
        assertThrows(IllegalArgumentException.class, () ->
                JobConfig.from(ConfigParser.parseJson(V5.replace("STREAMING", "BATCH"))));
    }
}
