package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.definition.PipelineDef;
import io.yak.ops.core.parser.YamlPipelineDefinitionParser;
import io.yak.ops.sync.runtime.config.PipelineExecutionPlan;
import org.junit.jupiter.api.Test;

/** Checks the Task-owned JSON-to-YAML boundary without a frontend YAML adapter. */
class PipelineYamlConverterTest {
    private static final String V6 = """
            {
              "source": {
                "type": "MYSQL_CDC", "datasourceId": "source",
                "startup": {"mode": "SPECIFIC_OFFSETS", "file": "mysql-bin.000123", "pos": 45678},
                "tables": [{"database": "app", "table": "orders"}]
              },
              "sink": {
                "type": "JDBC", "datasourceId": "target",
                "schemaPolicy": {"initial": "VERIFY",
                                 "evolution": {"enabled": false, "allowDropColumns": false}},
                "tables": [{"schema": "public", "table": "ods_orders",
                            "write": {"mode": "UPSERT", "primaryKeys": ["id"]}}]
              },
              "route": [{"source-table": "app.orders", "sink-table": "public.ods_orders"}],
              "transform": [],
              "pipeline": {"execution.runtime-mode": "STREAMING", "pipeline.table-scope": "SINGLE"}
            }
            """;

    @Test
    void convertsBusinessJsonToFlinkCdcPipelineDefinition() {
        ReadonlyConfig json = ConfigParser.parseJson(V6);
        String yaml = PipelineYamlConverter.convert(json);
        assertTrue(yaml.contains("scan.startup.mode"));
        assertTrue(yaml.contains("specific-offset"));
        assertFalse(yaml.contains("pipelineYaml"));
        PipelineDef definition = new YamlPipelineDefinitionParser().parse(yaml);
        assertEquals("mysql", definition.getSource().getType());
        assertEquals("jdbc", definition.getSink().getType());
        PipelineExecutionPlan plan = PipelineExecutionPlan.from(definition);
        assertEquals("MYSQL_CDC", plan.tables().getFirst().source().getSourceMap().get("type"));
        assertEquals("specific-offset", plan.tables().getFirst()
                .source().getSourceMap().get("scan.startup.mode"));
    }

    @Test
    void rejectsFrontendYamlAndUnsupportedJsonKeys() {
        assertThrows(RuntimeException.class,
                () -> PipelineYamlConverter.convert(ConfigParser.parseJson(
                        "{\"pipelineYaml\": \"source: {}\"}")));
        assertThrows(RuntimeException.class,
                () -> PipelineYamlConverter.convert(ConfigParser.parseJson(
                        V6.replace("\"datasourceId\": \"source\"",
                                "\"datasourceId\": \"source\", \"password\": \"secret\""))));
    }

    @Test
    void recomputesWithoutTrustingClientIndex() {
        ReadonlyConfig json = ConfigParser.parseJson(
                V6.replace("\"transform\": []", "\"index\": {\"sourceType\": \"JDBC\"}, \"transform\": []"));
        String yaml = PipelineYamlConverter.convert(json);
        PipelineExecutionPlan plan = PipelineExecutionPlan.from(new YamlPipelineDefinitionParser().parse(yaml));
        assertEquals("MYSQL_CDC", plan.solutionType().sourceIdentifier());
    }

    @Test
    void rejectsRoutesToUndeclaredDestinations() {
        String changed = V6.replace(
                "\"sink-table\": \"public.ods_orders\"",
                "\"sink-table\": \"public.other_table\"");
        assertThrows(RuntimeException.class,
                () -> PipelineYamlConverter.convert(ConfigParser.parseJson(changed)));
    }

    @Test
    void treatsBlankDatabaseAsAbsentForSchemaNamespaces() {
        String json = V6.replace("\"database\": \"app\"",
                        "\"database\": \"\", \"schema\": \"app\"")
                .replace("\"schema\": \"public\"",
                        "\"database\": \"\", \"schema\": \"public\"");
        String yaml = PipelineYamlConverter.convert(ConfigParser.parseJson(json));
        PipelineDef definition = new YamlPipelineDefinitionParser().parse(yaml);
        assertEquals("SCHEMA", definition.getSource().getConfig().toMap().get("yak.namespace-kind"));
        assertEquals("SCHEMA", definition.getSink().getConfig().toMap().get("yak.namespace-kind"));
        PipelineExecutionPlan plan = PipelineExecutionPlan.from(definition);
        assertEquals("app", plan.tables().getFirst().source().getSourceMap().get("schema"));
        assertEquals("public", plan.tables().getFirst().sink().getSourceMap().get("schema"));
    }

    @Test
    void retainsSnapshotOnlyAsBatch() {
        String json = V6.replace(
                "\"mode\": \"SPECIFIC_OFFSETS\", \"file\": \"mysql-bin.000123\", \"pos\": 45678",
                "\"mode\": \"SNAPSHOT\"")
                .replace("\"execution.runtime-mode\": \"STREAMING\"",
                        "\"execution.runtime-mode\": \"BATCH\"");
        PipelineExecutionPlan plan = PipelineExecutionPlan.from(
                new YamlPipelineDefinitionParser().parse(
                        PipelineYamlConverter.convert(ConfigParser.parseJson(json))));
        assertEquals(1, plan.steps().size());
    }
}
