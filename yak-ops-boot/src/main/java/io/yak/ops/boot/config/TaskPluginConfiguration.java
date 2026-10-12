package io.yak.ops.boot.config;

import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.datasync.SyncPlugin;
import io.yak.ops.plugin.task.datasync.SyncRuntimeResources;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicitly constructs and registers built-in Task plugins with their runtime dependencies. */
@Configuration(proxyBeanMethods = false)
public class TaskPluginConfiguration {

    @Bean
    PipelineExecutor taskPipelineExecutor() {
        return new EmbeddedPipelineExecutor();
    }

    @Bean
    SyncPlugin syncPlugin(
            SyncRuntimeResources resources,
            PipelineExecutor executor,
            @Value("${yak.task.execution.state-directory:./data/task-state}") String stateDirectory) {
        return new SyncPlugin(resources, executor, Path.of(stateDirectory));
    }

    @Bean
    TaskPluginRegistry taskPluginRegistry(SyncPlugin syncPlugin) {
        return new TaskPluginRegistry(List.of(syncPlugin));
    }
}
