package org.apache.flink.cdc.cli.utils;

import org.apache.flink.cdc.common.configuration.Configuration;
import org.apache.flink.core.fs.Path;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Utilities for handling {@link Configuration}. */
public class ConfigurationUtils {

    private static final String KEY_SEPARATOR = ".";

    public static Configuration loadConfigFile(Path configPath) throws Exception {
        return loadConfigFile(configPath, false);
    }

    public static Configuration loadConfigFile(Path configPath, boolean allowDuplicateKeys)
            throws Exception {
        Map<String, Object> configMap =
                YamlParserUtils.loadYamlFile(
                        new File(configPath.toUri().getPath()), allowDuplicateKeys);
        return Configuration.fromMap(flattenConfigMap(configMap, ""));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> flattenConfigMap(
            Map<String, Object> config, String keyPrefix) {
        final Map<String, String> flattenedMap = new HashMap<>();

        config.forEach(
                (key, value) -> {
                    String flattenedKey = keyPrefix + key;
                    if (value instanceof Map) {
                        Map<String, Object> e = (Map<String, Object>) value;
                        flattenedMap.putAll(flattenConfigMap(e, flattenedKey + KEY_SEPARATOR));
                    } else if (value != null) {
                        if (value instanceof List) {
                            flattenedMap.put(flattenedKey, YamlParserUtils.toYAMLString(value));
                        } else {
                            flattenedMap.put(flattenedKey, value.toString());
                        }
                    }
                });

        return flattenedMap;
    }
}
