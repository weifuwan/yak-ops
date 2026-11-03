package io.yak.ops.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.boot.config.TaskPluginConfiguration;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** The deleted data sync runtime must not register an executable Task plugin. */
class TaskPluginAssemblyTest {
    @Test
    void doesNotRegisterRemovedDataSyncPlugin() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(TaskPluginConfiguration.class)) {
            TaskPluginManager registry = context.getBean(TaskPluginManager.class);
            assertEquals(List.of(), registry.types());
            assertThrows(TaskPluginException.class, () -> registry.get("DATA_SYNC"));
        }
    }
}
