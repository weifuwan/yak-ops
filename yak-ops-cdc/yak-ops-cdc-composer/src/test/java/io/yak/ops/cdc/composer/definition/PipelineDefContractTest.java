package io.yak.ops.cdc.composer.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.configuration.Configuration;
import io.yak.ops.cdc.common.event.SchemaChangeEventTypeFamily;
import io.yak.ops.cdc.common.pipeline.PipelineOptions;
import io.yak.ops.cdc.common.pipeline.RouteMode;
import io.yak.ops.cdc.common.pipeline.RuntimeExecutionMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PipelineDefContractTest {

    @Test
    void definitionPreservesAllFlinkCdcFields() {
        SourceDef source = new SourceDef(
                "mysql", "source", Configuration.fromMap(Map.of("tables", "app.orders")));
        SinkDef sink = new SinkDef("values", "sink", Configuration.fromMap(Map.of("password", "secret")));
        RouteDef route = new RouteDef("app.orders", "target.orders", null, "mapping");
        TransformDef transform = new TransformDef(
                "app.orders", "id, name", null, null, null, null, null, null);
        UdfDef udf = new UdfDef("test", "example.MyFunction", Map.of());
        ModelDef model = ModelDef.of("testModel", "dummy", Map.of());
        Configuration settings = Configuration.fromMap(Map.of("route-mode", "FIRST_MATCH"));
        PipelineDef def = new PipelineDef(
                source, sink, List.of(route), List.of(transform), List.of(udf), List.of(model), settings);

        assertEquals("mysql", def.getSource().getType());
        assertEquals("values", def.getSink().getType());
        assertEquals(RouteMode.FIRST_MATCH, def.getRouteMode());
        assertEquals(1, def.getRoute().size());
        assertEquals(1, def.getTransforms().size());
        assertEquals(1, def.getUdfs().size());
        assertEquals(1, def.getModels().size());
        assertEquals(RuntimeExecutionMode.STREAMING,
                def.getConfig().get(PipelineOptions.PIPELINE_EXECUTION_RUNTIME_MODE));
        assertTrue(def.getSink().getIncludedSchemaEvolutionTypes().size()
                == SchemaChangeEventTypeFamily.ALL.length);
        assertFalse(def.toString().contains("secret"));
    }

    @Test
    void rejectsInvalidTimeZone() {
        Configuration settings =
                Configuration.fromMap(Map.of("local-time-zone", "invalid-zone"));
        assertThrows(IllegalArgumentException.class, () -> new PipelineDef(
                new SourceDef("mysql", null, new Configuration()),
                new SinkDef("values", null, new Configuration()),
                List.of(), List.of(), List.of(), settings));
    }
}
