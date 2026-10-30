package io.yak.ops.core.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.definition.PipelineDef;
import org.junit.jupiter.api.Test;

class YamlPipelineDefinitionParserTest {
    private final PipelineDefinitionParser parser = new YamlPipelineDefinitionParser();

    @Test
    void parsesFlinkCdcStyleSourceSinkRouteAndPipeline() {
        PipelineDef definition = parser.parse("""
                source:
                  type: mysql
                  name: orders-source
                  datasource-id: source
                  tables: app.orders
                  scan.startup.mode: initial
                sink:
                  type: jdbc
                  datasource-id: sink
                  write.mode: UPSERT
                route:
                  - source-table: app.orders
                    sink-table: public.ods_orders
                    description: business routing
                pipeline:
                  name: orders-sync
                  parallelism: 1
                  execution.runtime-mode: STREAMING
                """);
        assertEquals("mysql", definition.getSource().getType());
        assertEquals("orders-source", definition.getSource().getName().orElseThrow());
        assertEquals("initial", definition.getSource().getConfig().getSourceMap().get("scan.startup.mode"));
        assertEquals("jdbc", definition.getSink().getType());
        assertEquals("public.ods_orders", definition.getRoute().getFirst().getSinkTable());
        assertTrue(definition.getRoute().getFirst().getDescription().isPresent());
        assertEquals("1", definition.getConfig().getSourceMap().get("parallelism"));
    }

    @Test
    void rejectsUnknownTopLevelAndDuplicateKeys() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                source: {type: mysql}
                sink: {type: jdbc}
                unexpected: value
                """));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                source: {type: mysql, type: jdbc}
                sink: {type: jdbc}
                """));
    }

    @Test
    void rejectsNestedConnectorConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                source:
                  type: mysql
                  startup: {mode: initial}
                sink: {type: jdbc}
                """));
    }
}
