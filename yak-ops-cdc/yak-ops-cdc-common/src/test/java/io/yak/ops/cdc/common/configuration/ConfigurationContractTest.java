package io.yak.ops.cdc.common.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.cdc.common.pipeline.RouteMode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigurationContractTest {

    @Test
    void typedOptionsAndDefaultsMatchUpstream() {
        Configuration configuration =
                Configuration.fromMap(Map.of("parallelism", "2", "route-mode", "FIRST_MATCH"));
        assertEquals(2, configuration.get(ConfigOptions.key("parallelism").intType().defaultValue(1)));
        assertEquals(
                RouteMode.FIRST_MATCH,
                configuration.get(
                        ConfigOptions.key("route-mode")
                                .enumType(RouteMode.class)
                                .defaultValue(RouteMode.ALL_MATCH)));
        assertFalse(configuration.contains(ConfigOptions.key("absent").stringType().noDefaultValue()));
    }

    @Test
    void configurationLogsNeverExposeSecrets() {
        Configuration configuration = Configuration.fromMap(Map.of("password", "my-secret-value"));
        assertFalse(configuration.toString().contains("my-secret-value"));
        assertThrows(
                IllegalArgumentException.class,
                () -> Configuration.fromMap(Map.of("parallelism", "my-secret-value"))
                        .get(ConfigOptions.key("parallelism").intType().noDefaultValue()));
    }
}
