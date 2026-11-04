package io.yak.ops.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.InputStreamResource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImmutableYamlDelegate implements IPropertyDelegate {

    private static final String REMOTE_LOGGING_YAML_NAME = "/remote-logging.yaml";

    private final Properties properties;

    public ImmutableYamlDelegate() {
        this(REMOTE_LOGGING_YAML_NAME);
    }

    public ImmutableYamlDelegate(String... yamlAbsolutePath) {
        properties = new Properties();
        // read from classpath
        for (String fileName : yamlAbsolutePath) {
            try (InputStream fis = ImmutableYamlDelegate.class.getResourceAsStream(fileName)) {
                if (fis == null) {
                    log.warn("Cannot find the file: {} under classpath", fileName);
                    continue;
                }
                YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
                factory.setResources(new InputStreamResource(fis));
                factory.afterPropertiesSet();
                Properties subProperties = factory.getObject();
                properties.putAll(subProperties);
            } catch (IOException e) {
                log.error("Load property: {} error, please check if the file exist under classpath",
                        yamlAbsolutePath, e);
                throw new RuntimeException(e);
            }
        }
        printProperties();
    }

    public ImmutableYamlDelegate(Properties properties) {
        this.properties = properties;
    }

    @Override
    public String get(String key) {
        return properties.getProperty(key);
    }

    @Override
    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    @Override
    public Set<String> getPropertyKeys() {
        return properties.stringPropertyNames();
    }

    private void printProperties() {
        properties.forEach((k, v) -> log.debug("Get property {} -> {}", k, v));
    }
}
