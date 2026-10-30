package io.yak.ops.cli.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Validates Flink CDC 3.7's recursive configuration flattening utility. */
class ConfigurationUtilsTest {

    @Test
    void flattensNestedConfigurationMapsWithoutChangingPipelineParserSyntax() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("execution", Map.of("runtime-mode", "BATCH"));
        nested.put("parallelism", 2);

        Map<String, String> flattened = ConfigurationUtils.flattenConfigMap(nested, "");
        assertEquals("BATCH", flattened.get("execution.runtime-mode"));
        assertEquals("2", flattened.get("parallelism"));
    }
}
