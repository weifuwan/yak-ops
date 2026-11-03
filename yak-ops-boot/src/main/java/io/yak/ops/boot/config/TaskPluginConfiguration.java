package io.yak.ops.boot.config;

import io.yak.ops.plugin.task.api.TaskPluginManager;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers built-in Task plugins. Data synchronization remains unavailable until a new
 * execution engine is implemented; unknown Task types are rejected by the registry.
 */
@Configuration(proxyBeanMethods = false)
public class TaskPluginConfiguration {
    @Bean
    TaskPluginManager taskPluginRegistry() {
        return TaskPluginManager.fromFactories(List.of());
    }
}
