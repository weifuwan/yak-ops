package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks the Task-owned JSON-to-YAML boundary without a frontend YAML adapter. */
class PipelineYamlConverterTest {
    private static final String PIPELINE_JSON = """
            {
              "source": {
                "type": "mysql", "datasourceId": "source",
                "startup": {"mode": "SPECIFIC_OFFSETS", "file": "mysql-bin.000123", "pos": 45678},
                "tables": [{"database": "app", "table": "orders"}]
              },
              "sink": {
                "type": "jdbc-postgres", "datasourceId": "target",
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
        ReadonlyConfig json = ConfigParser.parseJson(PIPELINE_JSON);
        String yaml = PipelineYamlConverter.convert(json);
        assertTrue(yaml.contains("scan.startup.mode"));
        assertTrue(yaml.contains("specific-offset"));
        assertFalse(yaml.contains("pipelineYaml"));
        PipelineDef definition = new YamlPipelineDefinitionParser().parse(yaml);
        assertEquals("mysql", definition.getSource().getType());
        assertEquals("jdbc-postgres", definition.getSink().getType());
        assertEquals("specific-offset", definition.getSource().getConfig().toMap().get("scan.startup.mode"));
    }

    @Test
    void rejectsFrontendYamlAndUnsupportedJsonKeys() {
        assertThrows(RuntimeException.class,
                () -> PipelineYamlConverter.convert(ConfigParser.parseJson(
                        "{\"pipelineYaml\": \"source: {}\"}")));
        assertThrows(RuntimeException.class,
                () -> PipelineYamlConverter.convert(ConfigParser.parseJson(
                        PIPELINE_JSON.replace("\"datasourceId\": \"source\"",
                                "\"datasourceId\": \"source\", \"password\": \"secret\""))));
    }

    @Test
    void recomputesWithoutTrustingClientIndex() {
        ReadonlyConfig json = ConfigParser.parseJson(
                PIPELINE_JSON.replace("\"transform\": []", "\"index\": {\"sourceType\": \"JDBC\"}, \"transform\": []"));
        String yaml = PipelineYamlConverter.convert(json);
        PipelineDef definition = new YamlPipelineDefinitionParser().parse(yaml);
        assertEquals("mysql", definition.getSource().getType());
        assertFalse(definition.getSource().getConfig().toMap().containsKey("index"));
    }

    @Test
    void rejectsRoutesToUndeclaredDestinations() {
        String changed = PIPELINE_JSON.replace(
                "\"sink-table\": \"public.ods_orders\"",
                "\"sink-table\": \"public.other_table\"");
        assertThrows(RuntimeException.class,
                () -> PipelineYamlConverter.convert(ConfigParser.parseJson(changed)));
    }

    @Test
    void treatsBlankDatabaseAsAbsentForSchemaNamespaces() {
        String json = PIPELINE_JSON.replace("\"database\": \"app\"",
                        "\"database\": \"\", \"schema\": \"app\"")
                .replace("\"schema\": \"public\"",
                        "\"database\": \"\", \"schema\": \"public\"");
        String yaml = PipelineYamlConverter.convert(ConfigParser.parseJson(json));
        PipelineDef definition = new YamlPipelineDefinitionParser().parse(yaml);
        assertEquals("SCHEMA", definition.getSource().getConfig().toMap().get("yak.namespace-kind"));
        assertEquals("SCHEMA", definition.getSink().getConfig().toMap().get("yak.namespace-kind"));
        assertEquals("app.orders", definition.getSource().getConfig().toMap().get("tables"));
        assertEquals("public.ods_orders", definition.getRoute().getFirst().getSinkTable());
    }

    @Test
    void retainsSnapshotOnlyAsBatch() {
        String json = PIPELINE_JSON.replace(
                "\"mode\": \"SPECIFIC_OFFSETS\", \"file\": \"mysql-bin.000123\", \"pos\": 45678",
                "\"mode\": \"SNAPSHOT\"")
                .replace("\"execution.runtime-mode\": \"STREAMING\"",
                        "\"execution.runtime-mode\": \"BATCH\"");
        PipelineDef definition = new YamlPipelineDefinitionParser().parse(
                PipelineYamlConverter.convert(ConfigParser.parseJson(json)));
        assertEquals("BATCH", definition.getConfig().toMap().get("execution.runtime-mode"));
        assertEquals("snapshot", definition.getSource().getConfig().toMap().get("scan.startup.mode"));
    }
    @Test
    void retainsTransformProjectionAndFilter() {
        String json = PIPELINE_JSON.replace("\"transform\": []", """
                "transform": [{"source-table": "app.orders", "projection": "id, amount * 2 AS amount",
                               "filter": "amount > 10"}]
                """);
        PipelineDef definition = new YamlPipelineDefinitionParser().parse(
                PipelineYamlConverter.convert(ConfigParser.parseJson(json)));
        assertEquals("id, amount * 2 AS amount", definition.getTransforms().getFirst().getProjection());
        assertEquals("amount > 10", definition.getTransforms().getFirst().getFilter());
    }

    @Test
    void preservesFactoryIdentifierWithoutSelectingAProductSolution() {
        String json = PIPELINE_JSON.replace("\"type\": \"mysql\"", "\"type\": \"custom-source\"");
        PipelineDef definition = new YamlPipelineDefinitionParser().parse(
                PipelineYamlConverter.convert(ConfigParser.parseJson(json)));
        assertEquals("custom-source", definition.getSource().getType());
    }

    @Test
    void rejectsLegacyConnectorNames() {
        assertThrows(RuntimeException.class, () -> PipelineYamlConverter.convert(
                ConfigParser.parseJson(PIPELINE_JSON.replace("\"type\": \"mysql\"", "\"type\": \"MYSQL_CDC\""))));
    }

    @Test
    void preservesSupportedUpstreamPipelineOptions() {
        String json = PIPELINE_JSON.replace("\"pipeline.table-scope\": \"SINGLE\"", """
                "pipeline.table-scope": "SINGLE", "name": "orders", "parallelism": 2,
                "local-time-zone": "Asia/Shanghai", "schema.change.behavior": "exception",
                "schema.operator.uid": "orders-schema", "route-mode": "FIRST_MATCH",
                "transform.decimal.precision.mode": "UP_TO_38"
                """);
        PipelineDef definition = new YamlPipelineDefinitionParser().parse(
                PipelineYamlConverter.convert(ConfigParser.parseJson(json)));
        assertEquals("orders", definition.getConfig().toMap().get("name"));
        assertEquals("2", definition.getConfig().toMap().get("parallelism"));
        assertEquals("Asia/Shanghai", definition.getConfig().toMap().get("local-time-zone"));
        assertEquals("exception", definition.getConfig().toMap().get("schema.change.behavior"));
        assertEquals("orders-schema", definition.getConfig().toMap().get("schema.operator.uid"));
        assertEquals("FIRST_MATCH", definition.getConfig().toMap().get("route-mode"));
        assertEquals("UP_TO_38", definition.getConfig().toMap().get("transform.decimal.precision.mode"));
    }

    @Test
    void rejectsPipelineOptionsWithoutRuntimeConsumers() {
        assertThrows(RuntimeException.class, () -> PipelineYamlConverter.convert(ConfigParser.parseJson(
                PIPELINE_JSON.replace("\"pipeline.table-scope\": \"SINGLE\"",
                        "\"pipeline.table-scope\": \"SINGLE\", \"schema-operator.rpc-timeout\": \"1 min\""))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsMixedNamespaceShapesBeforeResolvingRoutes() {
        Map<String, Object> root = new LinkedHashMap<>(ConfigParser.parseJson(PIPELINE_JSON).getSourceMap());
        Map<String, Object> source = new LinkedHashMap<>((Map<String, Object>) root.get("source"));
        Map<String, Object> sink = new LinkedHashMap<>((Map<String, Object>) root.get("sink"));
        source.put("tables", List.of(
                Map.of("database", "app", "schema", "public", "table", "orders"),
                Map.of("schema", "public", "table", "users")));
        sink.put("tables", List.of(
                Map.of("schema", "public", "table", "ods_orders", "write", Map.of("mode", "UPSERT")),
                Map.of("schema", "public", "table", "ods_users", "write", Map.of("mode", "UPSERT"))));
        root.put("source", source);
        root.put("sink", sink);
        root.put("route", List.of(
                Map.of("source-table", "app.public.orders", "sink-table", "public.ods_orders"),
                Map.of("source-table", "public.users", "sink-table", "public.ods_users")));
        assertThrows(RuntimeException.class, () -> PipelineYamlConverter.convert(ReadonlyConfig.fromMap(root)));

        source.put("tables", List.of(
                Map.of("schema", "public", "table", "orders"),
                Map.of("schema", "public", "table", "users")));
        sink.put("tables", List.of(
                Map.of("database", "ods", "schema", "public", "table", "orders", "write", Map.of("mode", "UPSERT")),
                Map.of("schema", "public", "table", "users", "write", Map.of("mode", "UPSERT"))));
        root.put("route", List.of(
                Map.of("source-table", "public.orders", "sink-table", "ods.public.orders"),
                Map.of("source-table", "public.users", "sink-table", "public.users")));
        assertThrows(RuntimeException.class, () -> PipelineYamlConverter.convert(ReadonlyConfig.fromMap(root)));
    }
}
