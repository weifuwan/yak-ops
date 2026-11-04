package io.yak.ops.plugin.task.api.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import io.yak.ops.common.utils.PropertyUtils;
import io.yak.ops.plugin.task.api.shell.bash.BashShellInterceptorBuilder;
import io.yak.ops.plugin.task.api.utils.AbstractCommandExecutorConstants;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class BaseLinuxShellInterceptorBuilderTest {

    @Test
    void generateBootstrapCommandTest() {
        BashShellInterceptorBuilder builder = new BashShellInterceptorBuilder()
                .shellDirectory("/tmp")
                .shellName("test")
                .runUser("root")
                .cpuQuota(10)
                .memoryQuota(1024)
                .sudoMode(false);
        try (MockedStatic<PropertyUtils> mockStatic = mockStatic(PropertyUtils.class)) {
            // default
            List<String> defaultCommands = builder.generateBootstrapCommand();
            assertEquals("bash /tmp/test.sh", String.join(" ", defaultCommands));

            // sudo mode
            builder.sudoMode(true);
            List<String> sudoCommands = builder.generateBootstrapCommand();
            assertEquals("sudo -u root -i /tmp/test.sh", String.join(" ", sudoCommands));

            // resource limit mode
            mockStatic.when(
                    () -> PropertyUtils.getBoolean(AbstractCommandExecutorConstants.TASK_RESOURCE_LIMIT_STATE, false))
                    .thenReturn(true);
            List<String> limitModeCommands = builder.generateBootstrapCommand();
            assertEquals("sudo systemd-run -q --scope -p CPUQuota=10% -p MemoryLimit=1024M --uid=root /tmp/test.sh",
                    String.join(" ", limitModeCommands));
        }
    }
}
