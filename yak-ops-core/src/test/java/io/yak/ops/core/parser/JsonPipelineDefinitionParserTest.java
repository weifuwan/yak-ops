package io.yak.ops.core.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.definition.PipelineDef;
import org.junit.jupiter.api.Test;

class JsonPipelineDefinitionParserTest {

    private final JsonPipelineDefinitionParser parser = new JsonPipelineDefinitionParser();

    private static final String V5 = """
            {
              "source": {
                "type": "MYSQL_CDC",
                "datasourceId": "mysql",
                "startup": {"mode": "INITIAL"},
                "tables": [{"database": "app", "table": "orders"}]
              },
              "sink": {
                "type": "JDBC",
                "datasourceId": "postgres",
                "schemaPolicy": {
                  "initial": "VERIFY",
                  "evolution": {"enabled": false, "allowDropColumns": false}
                },
                "tables": [{
                  "schema": "public", "table": "ods_orders",
                  "write": {"mode": "UPSERT"}
                }]
              },
              "route": [{
                "source-table": "app.orders",
                "sink-table": "public.ods_orders"
              }],
              "transform": [],
              "pipeline": {"execution.runtime-mode": "STREAMING"}
            }
            """;

    @Test
    void parsesFlinkStylePipelineDefinition() {
        PipelineDef definition = parser.parse(V5);
        assertEquals(5, definition.getVersion());
        assertEquals("MYSQL_CDC", definition.getSource().getType());
        assertEquals("JDBC", definition.getSink().getType());
        assertEquals("app.orders", definition.getRoute().getFirst().getSourceTable());
        assertTrue(definition.getTransforms().isEmpty());
    }

    @Test
    void rejectsUnimplementedTransform() {
        String invalid = V5.replace("\"transform\": []",
                "\"transform\": [{\"source-table\": \"app.orders\", \"filter\": \"id > 1\"}]");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(invalid));
    }

    @Test
    void rejectsUnknownFieldsAndUnmappedTables() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(V5.replace(
                "\"transform\": []", "\"unexpected\": 1, \"transform\": []")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(V5.replace(
                "\"source-table\": \"app.orders\"", "\"source-table\": \"different\"")
                .replace("\"route\":", "\"route\":")));
    }

    @Test
    void retainsLegacyV4InputForPinnedDefinitions() {
        PipelineDef definition = parser.parse("""
                {"solutionType": "SINGLE_TABLE_OFFLINE", "steps": ["FULL"]}
                """);
        assertEquals(4, definition.getVersion());
    }
}
