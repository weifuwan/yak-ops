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
