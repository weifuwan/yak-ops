package io.yak.ops.cdc.cli.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.configuration.Configuration;
import io.yak.ops.cdc.common.event.SchemaChangeEventType;
import io.yak.ops.cdc.common.pipeline.ExistingTableSchemaExpansionMode;
import io.yak.ops.cdc.common.pipeline.PipelineOptions;
import io.yak.ops.cdc.common.pipeline.RouteMode;
import io.yak.ops.cdc.common.pipeline.RuntimeExecutionMode;
import io.yak.ops.cdc.composer.definition.PipelineDef;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Regression tests using Apache Flink CDC 3.7 pipeline definition fixtures. */
class YamlPipelineDefinitionParserTest {

    private final YamlPipelineDefinitionParser parser = new YamlPipelineDefinitionParser();

    private Path fixture(String name) throws URISyntaxException {
        return Path.of(getClass().getResource("/definitions/" + name).toURI());
    }

    @Test
    void minimizedDefinitionDefaultsToStreaming() throws Exception {
        PipelineDef definition = parser.parse(
                fixture("pipeline-definition-minimized.yaml"), new Configuration());
        assertEquals("mysql", definition.getSource().getType());
        assertEquals("kafka", definition.getSink().getType());
        assertTrue(definition.getRoute().isEmpty());
        assertTrue(definition.getTransforms().isEmpty());
        assertEquals(RuntimeExecutionMode.STREAMING,
                definition.getConfig().get(PipelineOptions.PIPELINE_EXECUTION_RUNTIME_MODE));
        assertNotNull(definition.getConfig().get(PipelineOptions.PIPELINE_LOCAL_TIME_ZONE));
    }

    @Test
    void fullDefinitionPreservesTransformsRoutesModelAndConfig() throws Exception {
        PipelineDef definition = parser.parse(
                fixture("pipeline-definition-full.yaml"), new Configuration());
        assertEquals("source-database", definition.getSource().getName().orElseThrow());
        assertEquals("sink-queue", definition.getSink().getName().orElseThrow());
        assertEquals(2, definition.getRoute().size());
        assertEquals(2, definition.getTransforms().size());
        assertEquals("id", definition.getTransforms().get(0).getPrimaryKeys());
        assertEquals("GET_EMBEDDING", definition.getModels().get(0).getName());
        assertTrue(definition.getModels().get(0).isLegacy());
        assertEquals(4, definition.getConfig().get(PipelineOptions.PIPELINE_PARALLELISM));
        assertFalse(definition.getConfig().getKeys().contains("flink-conf"));
        assertFalse(definition.toString().contains("abcd1234"));
    }

    @Test
    void globalOverridesApplyOnlyWhenLocalConfigurationDoesNotOverride() throws Exception {
        PipelineDef definition = parser.parse(
                fixture("pipeline-definition-with-optional.yaml"),
                Configuration.fromMap(Map.of("parallelism", "13", "route-mode", "FIRST_MATCH")));
        assertEquals(4, definition.getConfig().get(PipelineOptions.PIPELINE_PARALLELISM));
        assertEquals(RouteMode.FIRST_MATCH, definition.getRouteMode());
    }

    @Test
    void routeModeAndReplacementSymbolParse() throws Exception {
        PipelineDef routed = parser.parse(
                fixture("pipeline-definition-with-route-mode.yaml"), new Configuration());
        assertEquals(RouteMode.FIRST_MATCH, routed.getRouteMode());
        assertEquals("<>", routed.getRoute().get(2).getReplaceSymbol().orElseThrow());

        PipelineDef full = parser.parse(
                fixture("pipeline-definition-full-with-repsym.yaml"), new Configuration());
        assertEquals("<>", full.getRoute().get(0).getReplaceSymbol().orElseThrow());
    }

    @Test
    void udfDefinitionsIncludeJavaPythonAndOptions() throws Exception {
        PipelineDef javaUdf = parser.parse(
                fixture("pipeline-definition-with-udf.yaml"), new Configuration());
        assertEquals(2, javaUdf.getUdfs().size());
        PipelineDef python = parser.parse(
                fixture("pipeline-definition-with-python-udf.yaml"), new Configuration());
        assertEquals("py_identity", python.getUdfs().get(0).getName());
        assertTrue(python.getUdfs().get(0).getOptions().get("source").contains("def eval"));
        assertEquals("/flink/usrlib/deps.zip,/flink/usrlib/shared",
                python.getUdfs().get(0).getOptions().get("python-files"));
        PipelineDef opts = parser.parse(
                fixture("pipeline-definition-with-udf-options.yaml"), new Configuration());
        assertEquals("6379", opts.getUdfs().get(0).getOptions().get("port"));
    }

    @Test
    void sinkSchemaEvolutionOptionsAndExpansionMode() throws Exception {
        String yaml = "source:\n  type: mysql\nsink:\n  type: values\n"
                + "  include.schema.changes:\n    - add.column\n    - drop.table\n"
                + "  exclude.schema.changes:\n    - drop.table\n"
                + "  existing-table.schema-expansion.mode: CHECK\n";
        PipelineDef def = parser.parse(yaml, new Configuration());
        assertTrue(def.getSink().getIncludedSchemaEvolutionTypes().contains(SchemaChangeEventType.ADD_COLUMN));
        assertTrue(def.getSink().getIncludedSchemaEvolutionTypes().contains(SchemaChangeEventType.CREATE_TABLE));
        assertFalse(def.getSink().getIncludedSchemaEvolutionTypes().contains(SchemaChangeEventType.DROP_TABLE));
        assertEquals(ExistingTableSchemaExpansionMode.CHECK,
                def.getSink().getExistingTableSchemaExpansionMode());
    }

    @Test
    void parsesYamlFromString() throws Exception {
        PipelineDef def = parser.parse(
                "source:\n  type: mysql\nsink:\n  type: values\n", new Configuration());
        assertEquals("mysql", def.getSource().getType());
        assertEquals("values", def.getSink().getType());
    }

    @Test
    void rejectsInvalidRootMissingAndUnexpectedFields() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("[]", new Configuration()));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(
                "source:\n  type: mysql\n", new Configuration()));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(
                "source:\n  type: mysql\nsink:\n  type: values\nunknown: 42\n",
                new Configuration()));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(
                "source:\n  type: mysql\nsink:\n  type: values\npipeline:\n  - invalid\n",
                new Configuration()));
    }

    @Test
    void rejectsDuplicateYamlKeysWithoutExposingValues() {
        String yaml = "source:\n  type: mysql\n  type: private-password\n"
                + "sink:\n  type: values\n";
        Exception error = assertThrows(Exception.class,
                () -> parser.parse(yaml, new Configuration()));
        assertFalse(error.getMessage().contains("private-password"));
    }

    @Test
    void reportsUnexpectedSinkOptionsWithoutLeakingRawValues() {
        String yaml = "source:\n  type: mysql\nsink:\n  type: values\n"
                + "route:\n  - source-table: a.b\n    sink-table: b.c\n    unexpected: super-secret\n";
        Exception error = assertThrows(Exception.class, () -> parser.parse(yaml, new Configuration()));
        assertFalse(error.getMessage().contains("super-secret"));
    }
}
