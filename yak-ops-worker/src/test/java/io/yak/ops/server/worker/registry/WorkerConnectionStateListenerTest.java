package io.yak.ops.server.worker.registry;

import static org.mockito.Mockito.times;

import io.yak.ops.registry.api.ConnectionState;
import io.yak.ops.registry.api.RegistryClient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * worker registry test
 */
@ExtendWith(MockitoExtension.class)
public class WorkerConnectionStateListenerTest {

    @InjectMocks
    private WorkerConnectionStateListener workerConnectionStateListener;

    @Mock
    private RegistryClient registryClient;

    @Test
    public void testWorkerConnectionStateListener() {
        Mockito.when(registryClient.getStoppable(io.yak.ops.registry.api.enums.RegistryNodeType.WORKER)).thenReturn(cause -> {
            // do nothing
        });

        workerConnectionStateListener.onUpdate(ConnectionState.CONNECTED);
        workerConnectionStateListener.onUpdate(ConnectionState.RECONNECTED);
        workerConnectionStateListener.onUpdate(ConnectionState.SUSPENDED);
        Mockito.verify(registryClient, times(0)).getStoppable();
        workerConnectionStateListener.onUpdate(ConnectionState.DISCONNECTED);
        Mockito.verify(registryClient, times(1)).getStoppable();
    }
}
