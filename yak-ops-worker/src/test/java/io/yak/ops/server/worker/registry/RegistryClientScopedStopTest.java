package io.yak.ops.server.worker.registry;

import io.yak.ops.common.IStoppable;
import io.yak.ops.registry.api.Registry;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.enums.RegistryNodeType;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RegistryClientScopedStopTest {

    @Test
    void masterAndWorkerHaveIndependentStopCallbacks() {
        RegistryClient registryClient = new RegistryClient(Mockito.mock(Registry.class));
        AtomicInteger masterStops = new AtomicInteger();
        AtomicInteger workerStops = new AtomicInteger();
        IStoppable master = reason -> masterStops.incrementAndGet();
        IStoppable worker = reason -> workerStops.incrementAndGet();

        registryClient.setStoppable(RegistryNodeType.MASTER, master);
        registryClient.setStoppable(RegistryNodeType.WORKER, worker);

        registryClient.getStoppable(RegistryNodeType.MASTER).stop("master");
        Assertions.assertEquals(1, masterStops.get());
        Assertions.assertEquals(0, workerStops.get());

        registryClient.getStoppable(RegistryNodeType.WORKER).stop("worker");
        Assertions.assertEquals(1, masterStops.get());
        Assertions.assertEquals(1, workerStops.get());
    }

    @Test
    void unregisteredRoleCannotReceiveStopCallback() {
        RegistryClient registryClient = new RegistryClient(Mockito.mock(Registry.class));
        Assertions.assertThrows(IllegalStateException.class,
                () -> registryClient.getStoppable(RegistryNodeType.MASTER));
    }
}
