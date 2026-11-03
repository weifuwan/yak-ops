package io.yak.ops.cdc.cli.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.configuration.Configuration;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigurationUtilsTest {

    private Path fixture(String name) throws URISyntaxException {
        return Path.of(getClass().getResource("/definitions/" + name).toURI());
    }

    @Test
    void loadsNestedConfigurationsAndFlattensMap() throws Exception {
        Configuration config = ConfigurationUtils.loadConfigFile(fixture("pipeline-definition-minimized.yaml"));
        assertEquals("mysql", config.toMap().get("source.type"));
        assertEquals("kafka", config.toMap().get("sink.type"));
        Map<String, String> flattened = ConfigurationUtils.flattenConfigMap(
                Map.of("outer", Map.of("key", 12, "list", List.of("a", "b"))), "");
        assertEquals("12", flattened.get("outer.key"));
        assertTrue(flattened.get("outer.list").contains("a"));
    }

    @Test
    void rejectsDuplicateKeysByDefault() throws Exception {
        Path duplicate = java.nio.file.Files.createTempFile("yak-cdc-config-", ".yaml");
        try {
            java.nio.file.Files.writeString(duplicate, "key: first\nkey: second\n");
            Exception error = assertThrows(Exception.class, () -> ConfigurationUtils.loadConfigFile(duplicate));
            assertFalse(error.getMessage().contains("second"));
            Configuration legacy = ConfigurationUtils.loadConfigFile(duplicate, true);
            assertEquals("second", legacy.toMap().get("key"));
        } finally {
            java.nio.file.Files.deleteIfExists(duplicate);
        }
    }

    @Test
    @SuppressWarnings("removal") // The deprecated helper is tested only for legacy parsing parity.
    void flinksOriginalConfigurationExtractionIsReadOnly() throws Exception {
        Map<String, String> options = io.yak.ops.cdc.cli.parser.YamlPipelineDefinitionParser
                .getFlinkConfigFromPipelineDef(fixture("pipeline-definition-full.yaml"));
        assertEquals("12min", options.get("execution.checkpointing.timeout"));
    }
}
