package io.yak.ops.plugin.task.remoteshell;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

import io.yak.ops.plugin.datasource.ssh.param.SSHConnectionParam;
import io.yak.ops.plugin.datasource.ssh.param.SSHDataSourceParamDTO;
import io.yak.ops.plugin.datasource.ssh.param.SSHDataSourceProcessor;
import io.yak.ops.plugin.task.api.TaskExecutionContext;

import org.apache.sshd.client.session.ClientSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@Disabled
@ExtendWith(MockitoExtension.class)
public class RemoteShellTaskTest {

    private String connectJson =
            "{\"user\":\"root\",\"password\":\"123456\",\"host\":\"dolphinscheduler.com\",\"port\":22, \"privateKey\":\"ssh-rsa AAAAB\"}";

    SSHConnectionParam sshConnectionParam;

    ClientSession clientSession;

    @BeforeEach
    void init() {
        SSHDataSourceProcessor sshDataSourceProcessor = new SSHDataSourceProcessor();
        SSHDataSourceParamDTO sshDataSourceParamDTO =
                (SSHDataSourceParamDTO) sshDataSourceProcessor.createDatasourceParamDTO(connectJson);
        sshConnectionParam = sshDataSourceProcessor.createConnectionParams(sshDataSourceParamDTO);
        clientSession = Mockito.mock(ClientSession.class, RETURNS_DEEP_STUBS);
    }

    @Test
    void testBuildCommand() throws Exception {
        TaskExecutionContext taskExecutionContext = new TaskExecutionContext();
        taskExecutionContext.setTaskAppId("1");
        taskExecutionContext
                .setTaskParams("{\"localParams\":[],\"rawScript\":\"echo 1\",\"resourceList\":[]}");
        taskExecutionContext.setExecutePath("/tmp");
        taskExecutionContext.setEnvironmentConfig("export PATH=/opt/anaconda3/bin:$PATH");
        RemoteShellTask remoteShellTask = spy(new RemoteShellTask(taskExecutionContext));
        doNothing().when(remoteShellTask).initRemoteExecutor();
        remoteShellTask.init();

        MockedStatic<Files> filesMockedStatic = org.mockito.Mockito.mockStatic(Files.class);
        filesMockedStatic.when(() -> Files.exists(Mockito.any())).thenReturn(false);
        String script = "#!/bin/bash\n" +
                "export PATH=/opt/anaconda3/bin:$PATH\n" +
                "echo 1\n" +
                "echo DOLPHINSCHEDULER-REMOTE-SHELL-TASK-STATUS-$?";
        Path path = Paths.get("/tmp/1_node.sh");
        filesMockedStatic.when(() -> Files.write(path, script.getBytes(), StandardOpenOption.APPEND))
                .thenThrow(new IOException("script match"));

        IOException exception = Assertions.assertThrows(IOException.class, () -> {
            remoteShellTask.buildCommand();
        });
        Assertions.assertEquals("script match", exception.getMessage());
    }

}
