package io.yak.ops.server.worker.registry;

import static org.mockito.BDDMockito.given;

import io.yak.ops.common.IStoppable;
import io.yak.ops.common.model.Server;
import io.yak.ops.common.utils.NetUtils;
import io.yak.ops.meter.metrics.MetricsProvider;
import io.yak.ops.meter.metrics.SystemMetrics;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.enums.RegistryNodeType;
import io.yak.ops.server.worker.config.WorkerConfig;
import io.yak.ops.server.worker.config.WorkerServerLoadProtection;
import io.yak.ops.server.worker.executor.PhysicalTaskExecutorContainerProvider;
import io.yak.ops.task.executor.container.ExclusiveThreadTaskExecutorContainer;
import io.yak.ops.task.executor.container.TaskExecutorContainerConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.Lists;

/**
 * worker registry test
 */
@ExtendWith(MockitoExtension.class)
public class WorkerRegistryClientTest {

    @InjectMocks
    private WorkerRegistryClient workerRegistryClient;
    @Mock
    private RegistryClient registryClient;
    @Mock
    private WorkerServerLoadProtection workerServerLoadProtection;
    @Mock
    private WorkerConfig workerConfig;
    @Mock
    private MetricsProvider metricsProvider;
    @Mock
    private PhysicalTaskExecutorContainerProvider physicalTaskExecutorContainerDelegator;
    @Mock
    private IStoppable stoppable;

    @Test
    public void testWorkerRegistryClientbasic() {

        final TaskExecutorContainerConfig containerConfig = TaskExecutorContainerConfig.builder()
                .taskExecutorThreadPoolSize(10)
                .containerName("test")
                .build();
        final ExclusiveThreadTaskExecutorContainer container =
                new ExclusiveThreadTaskExecutorContainer(containerConfig);
        given(physicalTaskExecutorContainerDelegator.getExecutorContainer()).willReturn(container);

        given(workerConfig.getWorkerAddress()).willReturn(NetUtils.getAddr(1234));
        given(workerConfig.getMaxHeartbeatInterval()).willReturn(Duration.ofSeconds(1));
        given(metricsProvider.getSystemMetrics()).willReturn(new SystemMetrics());
        given(registryClient.checkNodeExists(Mockito.anyString(), Mockito.any(RegistryNodeType.class)))
                .willReturn(true);

        workerRegistryClient.initWorkRegistry();
        workerRegistryClient.start();

        workerRegistryClient.setRegistryStoppable(stoppable);
    }

    @Test
    public void testWorkerRegistryClientgetAlertServerAddress() {
        given(registryClient.getServerList(Mockito.any(RegistryNodeType.class))).willReturn(new ArrayList<>());
        Assertions.assertEquals(workerRegistryClient.getAlertServerAddress(), Optional.empty());
        Mockito.reset(registryClient);
        String host = "test";
        Integer port = 1;
        Server server = new Server();
        server.setHost(host);
        server.setPort(port);
        given(registryClient.getServerList(Mockito.any(RegistryNodeType.class))).willReturn(Lists.newArrayList(server));
        Assertions.assertEquals(workerRegistryClient.getAlertServerAddress().get().getAddress(),
                String.format("%s:%d", host, port));
    }
}
