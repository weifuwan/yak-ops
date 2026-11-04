package io.yak.ops.server.worker.utils;

import io.yak.ops.common.utils.FileUtils;
import io.yak.ops.plugin.task.api.TaskExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TaskExecutionContextUtilsTest {

    @Test
    void createTaskInstanceWorkingDirectory() throws IOException {
        TaskExecutionContext taskExecutionContext = new TaskExecutionContext();
        taskExecutionContext.setTenantCode("tenantCode");
        taskExecutionContext.setWorkflowDefinitionCode(1L);
        taskExecutionContext.setWorkflowDefinitionVersion(1);
        taskExecutionContext.setWorkflowInstanceId(1);
        taskExecutionContext.setTaskInstanceId(1);

        String taskWorkingDirectory =
                FileUtils.getTaskInstanceWorkingDirectory(taskExecutionContext.getTaskInstanceId());
        try {
            // Test if the working directory is exist
            // will delete it and recreate
            FileUtils.createDirectoryWithPermission(Paths.get(taskWorkingDirectory), FileUtils.PERMISSION_775);
            Files.createFile(Paths.get(taskWorkingDirectory, "text.txt"));
            Assertions.assertTrue(Files.exists(Paths.get(taskWorkingDirectory, "text.txt")));

            TaskExecutionContextUtils.createTaskInstanceWorkingDirectory(taskExecutionContext);

            Assertions.assertEquals(taskWorkingDirectory, taskExecutionContext.getExecutePath());
            Assertions.assertFalse(Files.exists(Paths.get(taskWorkingDirectory, "text.txt")));
        } finally {
            FileUtils.deleteFile(taskWorkingDirectory);
        }
    }

    @Test
    void clearTaskInstanceWorkingDirectory() throws IOException {
        TaskExecutionContext taskExecutionContext = new TaskExecutionContext();
        taskExecutionContext.setTaskInstanceId(1);

        TaskExecutionContextUtils.createTaskInstanceWorkingDirectory(taskExecutionContext);
        String taskWorkingDirectory =
                FileUtils.getTaskInstanceWorkingDirectory(taskExecutionContext.getTaskInstanceId());
        Files.createFile(Paths.get(taskWorkingDirectory, "1.sh"));

        // Test delete the working directory
        TaskExecutionContextUtils.clearTaskInstanceWorkingDirectory(taskExecutionContext);
        Assertions.assertFalse(Files.exists(Paths.get(taskWorkingDirectory)));

        // Test do nothing if working directory is empty
        TaskExecutionContext emptyExecPathContext = new TaskExecutionContext();
        TaskExecutionContextUtils.clearTaskInstanceWorkingDirectory(emptyExecPathContext);
    }
}
