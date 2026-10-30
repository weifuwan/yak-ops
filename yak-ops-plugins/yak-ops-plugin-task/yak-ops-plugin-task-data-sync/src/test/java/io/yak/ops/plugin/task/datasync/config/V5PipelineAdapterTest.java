package io.yak.ops.plugin.task.datasync.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.definition.PipelineDef;
import io.yak.ops.core.parser.JsonPipelineDefinitionParser;
import io.yak.ops.plugin.task.datasync.solution.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

class V5PipelineAdapterTest {
    private static final String V5 = """
            {
              "source": {
                "type": "MYSQL_CDC", "datasourceId": "src",
                "startup": {"mode": "INITIAL"},
                "tables": [{"database": "app", "table": "orders"}]
              },
              "sink": {
                "type": "JDBC", "datasourceId": "dest",
                "schemaPolicy": {
                  "initial": "VERIFY",
                  "evolution": {"enabled": false, "allowDropColumns": false}
                },
                "tables": [{
                  "schema": "public", "table": "ods_orders",
                  "write": {"mode": "UPSERT"}
                }]
              },
              "route": [{"source-table": "app.orders", "sink-table": "public.ods_orders"}],
              "transform": [],
              "pipeline": {"execution.runtime-mode": "STREAMING"}
            }
            """;

    @Test
    void convertsV5IntoExistingV4JobPlannerContract() {
        PipelineDef definition = new JsonPipelineDefinitionParser().parse(V5);
        ReadonlyConfig converted = V5PipelineAdapter.toV4(definition);
        JobConfig config = JobConfig.from(converted);
        assertEquals(1, config.tables().size());
        assertEquals(List.of(Step.FULL, Step.INCREMENTAL),
                config.steps().stream().filter(step -> step != Step.SCHEMA).toList());
        assertEquals("orders", config.tables().getFirst().source().get(JobOptions.TABLE));
    }

    @Test
    void rejectsUnknownRouteTargets() {
        String invalid = V5.replace("public.ods_orders", "public.missing");
        assertThrows(IllegalArgumentException.class, () -> new JsonPipelineDefinitionParser().parse(invalid));
    }

    @Test
    void legacyV4RemainsUnmodified() {
        ReadonlyConfig legacy = io.yak.ops.configuration.ConfigParser.parseJson(
                "{\"solutionType\":\"SINGLE_TABLE_OFFLINE\"}");
        PipelineDef definition = PipelineDef.legacyV4(legacy);
        assertEquals(legacy, V5PipelineAdapter.toV4(definition));
    }
}
