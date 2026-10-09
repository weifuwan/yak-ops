package io.yak.ops.business.datasync.execution.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DataSyncExecutionControlTest {

    @Test
    void cancellationBeforeRuntimeLaunchPreventsSourceAndSinkStartup() {
        DataSyncExecutionRegistry registry = new DataSyncExecutionRegistry();
        DataSyncExecutionControl control = registry.reserve("execution-1");
        AtomicBoolean launcherInvoked = new AtomicBoolean();

        assertTrue(registry.cancel("execution-1"));
        assertTrue(control.isCanceled());
        assertNull(control.launch(() -> {
            launcherInvoked.set(true);
            throw new IllegalStateException("runtime must not start after cancellation");
        }));
        assertFalse(launcherInvoked.get());

        registry.remove("execution-1", control);
        assertFalse(registry.cancel("execution-1"));
    }

    @Test
    void reservationRejectsDuplicateOwnershipAndCannotRemoveAnotherHandle() {
        DataSyncExecutionRegistry registry = new DataSyncExecutionRegistry();
        DataSyncExecutionControl first = registry.reserve("execution-2");
        assertThrows(IllegalStateException.class, () -> registry.reserve("execution-2"));
        registry.remove("execution-2", new DataSyncExecutionControl());
        assertTrue(registry.cancel("execution-2"));
        registry.remove("execution-2", first);
        assertFalse(registry.cancel("execution-2"));
    }
}
