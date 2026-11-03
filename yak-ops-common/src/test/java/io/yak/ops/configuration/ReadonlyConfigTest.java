package io.yak.ops.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies nested, type-safe configuration without sharing mutable input collections. */
class ReadonlyConfigTest {

    @Test
    void nestedPathsUseTypedOptionsAndPreserveSourceKeys() {
        ReadonlyConfig config = ReadonlyConfig.fromMap(
                Map.of("source", Map.of("table", "orders", "batch", "64")));
        Option<String> table = Options.key("source.table").stringType().noDefaultValue();
        Option<Integer> batch = Options.key("source.batch").intType().noDefaultValue();

        assertEquals("orders", config.get(table));
        assertEquals(64, config.get(batch));
        assertTrue(config.contains(table));
        assertFalse(config.contains(Options.key("source.missing").stringType().noDefaultValue()));
    }

    @Test
    void typedListAndMapOptionsPreserveElementTypes() {
        ReadonlyConfig config = ReadonlyConfig.fromMap(Map.of(
                "tables", List.of("orders", "customers"),
                "limits", List.of(1, 2, 3),
                "properties", Map.of("ssl", "true")));
        assertEquals(
                List.of("orders", "customers"),
                config.get(Options.key("tables").listType().noDefaultValue()));
        assertEquals(
                List.of(1, 2, 3),
                config.get(Options.key("limits").listType(Integer.class).noDefaultValue()));
        assertEquals(
                Map.of("ssl", "true"),
                config.get(Options.key("properties").mapType().noDefaultValue()));
    }

    @Test
    void inputMutationDoesNotAlterReadonlyConfiguration() {
        List<Object> tables = new ArrayList<>(List.of("orders"));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("tables", tables);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("source", source);
        ReadonlyConfig config = ReadonlyConfig.fromMap(parameters);

        tables.add("customers");
        source.put("password", "later");
        parameters.clear();

        assertEquals(List.of("orders"), config.getSourceMap().get("source") instanceof Map<?, ?> values
                ? values.get("tables")
                : null);
        assertThrows(UnsupportedOperationException.class, () -> config.getSourceMap().put("x", "y"));
    }

    @Test
    void nestedSecretsAreRedactedFromDiagnostics() {
        ReadonlyConfig config = ReadonlyConfig.fromMap(
                Map.of("source", Map.of("password", "top-secret", "table", "orders")));
        assertFalse(config.toString().contains("top-secret"));
        assertTrue(config.toString().contains("orders"));
    }
}
