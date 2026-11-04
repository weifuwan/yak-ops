package io.yak.ops.api.executor.logging;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.extract.common.transportor.LogResponseStatus;
import io.yak.ops.extract.common.transportor.TaskInstanceLogFileDownloadResponse;
import io.yak.ops.extract.common.transportor.TaskInstanceLogPageQueryResponse;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.enums.RegistryNodeType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LogClientDelegateTest {

    @Mock
    private LocalLogClient localLogClient;

    @Mock
    private RemoteLogClient remoteLogClient;

    @Mock
    private RegistryClient registryClient;

    @InjectMocks
    private LogClientDelegate logClientDelegate;

    @Test
    public void testGetPartLogStringTaskInstanceNullThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> logClientDelegate.getPartLogString(null, 0, 10));
    }

    @Test
    public void testGetPartLogStringNodeExistsLocalSuccess() {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setId(1);
        taskInstance.setHost("localhost");
        taskInstance.setTaskType("SHELL");
        when(registryClient.checkNodeExists(eq(taskInstance.getHost()), any())).thenReturn(true);
        when(localLogClient.getPartLog(taskInstance, 0, 10))
                .thenReturn(new TaskInstanceLogPageQueryResponse("logContent", LogResponseStatus.SUCCESS, ""));
        String result = logClientDelegate.getPartLogString(taskInstance, 0, 10);
        assertEquals("logContent", result);
    }

    @Test
    public void testGetPartLogStringNodeExistsLocalFailure() {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setId(1);
        taskInstance.setHost("localhost");
        taskInstance.setTaskType("SHELL");

        when(registryClient.checkNodeExists("localhost", RegistryNodeType.WORKER)).thenReturn(true);
        when(localLogClient.getPartLog(taskInstance, 0, 10)).thenReturn(
                new TaskInstanceLogPageQueryResponse(null, LogResponseStatus.ERROR, "error"));
        when(remoteLogClient.getPartLog(taskInstance, 0, 10)).thenReturn("remoteLogContent");

        String result = logClientDelegate.getPartLogString(taskInstance, 0, 10);
        assertEquals("remoteLogContent", result);
    }

    @Test
    public void testGetPartLogStringNodeNotExists() {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setId(1);
        taskInstance.setHost("localhost");
        taskInstance.setTaskType("SHELL");

        when(registryClient.checkNodeExists("localhost", RegistryNodeType.WORKER)).thenReturn(false);
        when(remoteLogClient.getPartLog(taskInstance, 0, 10)).thenReturn("remoteLogContent");

        String result = logClientDelegate.getPartLogString(taskInstance, 0, 10);
        assertEquals("remoteLogContent", result);
    }

    @Test
    public void testGetWholeLogBytesTaskInstanceNullThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> logClientDelegate.getWholeLogBytes(null));
    }

    @Test
    public void testGetWholeLogBytesNodeExistsLocalSuccess() {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setId(1);
        taskInstance.setHost("localhost");
        taskInstance.setTaskType("SWITCH");

        when(registryClient.checkNodeExists("localhost", RegistryNodeType.MASTER)).thenReturn(true);
        when(localLogClient.getWholeLog(taskInstance)).thenReturn(
                new TaskInstanceLogFileDownloadResponse("logBytes".getBytes(), LogResponseStatus.SUCCESS, null));

        byte[] result = logClientDelegate.getWholeLogBytes(taskInstance);
        assertArrayEquals("logBytes".getBytes(), result);
    }

    @Test
    public void testGetWholeLogBytesNodeExistsLocalFailure() {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setId(1);
        taskInstance.setHost("localhost");
        taskInstance.setTaskType("SWITCH");

        when(registryClient.checkNodeExists("localhost", RegistryNodeType.MASTER)).thenReturn(true);
        when(localLogClient.getWholeLog(taskInstance)).thenReturn(
                new TaskInstanceLogFileDownloadResponse(null, LogResponseStatus.ERROR, "error"));
        when(remoteLogClient.getWholeLog(taskInstance)).thenReturn("remoteLogBytes".getBytes());

        byte[] result = logClientDelegate.getWholeLogBytes(taskInstance);
        assertArrayEquals("remoteLogBytes".getBytes(), result);
    }

    @Test
    public void testGetWholeLogBytesNodeNotExists() {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setId(1);
        taskInstance.setHost("localhost");
        taskInstance.setTaskType("SWITCH");

        when(registryClient.checkNodeExists("localhost", RegistryNodeType.MASTER)).thenReturn(false);
        when(remoteLogClient.getWholeLog(taskInstance)).thenReturn("remoteLogBytes".getBytes());

        byte[] result = logClientDelegate.getWholeLogBytes(taskInstance);
        assertArrayEquals("remoteLogBytes".getBytes(), result);
    }
}
