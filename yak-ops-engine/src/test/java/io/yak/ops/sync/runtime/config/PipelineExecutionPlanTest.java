package io.yak.ops.sync.runtime.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.sync.runtime.solution.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineExecutionPlanTest {
    @Test
    void directYamlDefinitionCreatesOneRealtimeRoute() {
        PipelineExecutionPlan job = job("""
                source:
                  type: mysql
                  datasource-id: source
                  tables: app.orders
                  scan.startup.mode: initial
                sink:
                  type: jdbc
                  datasource-id: target
                  yak.namespace-kind: SCHEMA
                  yak.schema.initial: VERIFY
                  yak.table.public.ods_orders.write.mode: UPSERT
                route:
                  - source-table: app.orders
                    sink-table: public.ods_orders
                pipeline:
                  execution.runtime-mode: STREAMING
                  yak.table-scope: SINGLE
                """);
        assertEquals(1, job.tables().size());
        assertEquals(List.of(Step.FULL, Step.INCREMENTAL), job.steps());
        assertEquals("MYSQL_CDC", job.tables().getFirst().source().getSourceMap().get("type"));
        assertEquals("ods_orders", job.tables().getFirst().sink().getSourceMap().get("table"));
    }

    @Test
    void retainsSpecificOffsetOptions() {
        PipelineExecutionPlan job = job("""
                source:
                  type: mysql
                  datasource-id: source
                  tables: app.orders
                  scan.startup.mode: specific-offset
                  scan.startup.specific-offset.file: mysql-bin.000001
                  scan.startup.specific-offset.pos: 1234
                sink:
                  type: jdbc
                  datasource-id: target
                  write.mode: UPSERT
                pipeline:
                  execution.runtime-mode: STREAMING
                """);
        assertEquals(List.of(Step.INCREMENTAL), job.steps());
        assertEquals("specific-offset",
                job.tables().getFirst().source().getSourceMap().get("scan.startup.mode"));
        assertTrue(job.tables().getFirst().source().getSourceMap()
                .containsKey("scan.startup.specific-offset.pos"));
    }

    @Test
    void refusesUnsupportedTransformInsteadOfSilentlyIgnoringIt() {
        assertThrows(IllegalArgumentException.class, () -> job("""
                source:
                  type: mysql
                  datasource-id: source
                  tables: app.orders
                sink:
                  type: jdbc
                  datasource-id: target
                transform:
                  - source-table: app.orders
                    filter: id > 10
                """));
    }

    private static PipelineExecutionPlan job(String yaml) {
        return PipelineExecutionPlan.from(new io.yak.ops.core.parser.YamlPipelineDefinitionParser().parse(yaml));
    }
}
