package io.yak.ops.common.utils;

import static io.yak.ops.common.constants.Constants.AWS_YAML_PATH;
import static io.yak.ops.common.constants.Constants.COMMON_PROPERTIES_PATH;
import static io.yak.ops.common.constants.Constants.REMOTE_LOGGING_YAML_PATH;
import static io.yak.ops.common.constants.Constants.RESOURCE_CENTER_YAML_PATH;

import io.yak.ops.common.config.ImmutablePriorityPropertyDelegate;
import io.yak.ops.common.config.ImmutablePropertyDelegate;
import io.yak.ops.common.config.ImmutableYamlDelegate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.common.base.Strings;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class PropertyUtils {

    // todo: add another implementation for zookeeper/etcd/consul/xx
    private final ImmutablePriorityPropertyDelegate propertyDelegate =
            new ImmutablePriorityPropertyDelegate(
                    new ImmutablePropertyDelegate(COMMON_PROPERTIES_PATH),
                    new ImmutableYamlDelegate(REMOTE_LOGGING_YAML_PATH, AWS_YAML_PATH, RESOURCE_CENTER_YAML_PATH));

    public static String getString(String key) {
        return propertyDelegate.get(key.trim());
    }

    public static String getString(String key, String defaultVal) {
        String val = getString(key);
        return Strings.isNullOrEmpty(val) ? defaultVal : val;
    }

    public static String getUpperCaseString(String key) {
        String val = getString(key);
        return Strings.isNullOrEmpty(val) ? val : val.toUpperCase();
    }

    public static Integer getInt(String key) {
        return propertyDelegate.getInt(key.trim());
    }

    public static Integer getInt(String key, int defaultValue) {
        return propertyDelegate.getInt(key, defaultValue);
    }

    public static Boolean getBoolean(String key) {
        return propertyDelegate.getBoolean(key);
    }

    public static Boolean getBoolean(String key, Boolean defaultValue) {
        return propertyDelegate.getBoolean(key, defaultValue);
    }

    public static Long getLong(String key) {
        return propertyDelegate.getLong(key);
    }

    public static Long getLong(String key, Long defaultValue) {
        return propertyDelegate.getLong(key, defaultValue);
    }

    /**
     * get all properties with specified prefix, like: fs.
     *
     * @param prefix prefix to search
     * @return all properties with specified prefix
     */
    public static Map<String, String> getByPrefix(String prefix) {
        Map<String, String> matchedProperties = new HashMap<>();
        for (String propName : propertyDelegate.getPropertyKeys()) {
            if (propName.startsWith(prefix)) {
                matchedProperties.put(propName, propertyDelegate.get(propName));
            }
        }
        return matchedProperties;
    }

    /**
     * Get all properties with specified prefix, like: fs., will replace the prefix with newPrefix
     */
    public static Map<String, String> getByPrefix(String prefix, String newPrefix) {
        Map<String, String> matchedProperties = new HashMap<>();
        for (String propName : propertyDelegate.getPropertyKeys()) {
            if (propName.startsWith(prefix)) {
                matchedProperties.put(propName.replace(prefix, newPrefix), propertyDelegate.get(propName));
            }
        }
        return matchedProperties;
    }

    public static <T> Set<T> getSet(String key, Function<String, Set<T>> transformFunction, Set<T> defaultValue) {
        return propertyDelegate.get(key, transformFunction, defaultValue);
    }
}
