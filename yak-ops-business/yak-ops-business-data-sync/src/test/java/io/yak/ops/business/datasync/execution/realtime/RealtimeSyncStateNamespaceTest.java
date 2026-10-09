package io.yak.ops.business.datasync.execution.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RealtimeSyncStateNamespaceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldReuseStateDirectoryAndEngineNameForSameTaskVersion() {
        RealtimeSyncStateNamespace manager = new RealtimeSyncStateNamespace(tempDirectory);

        Path first = manager.stateDirectory("workspace-1", "task-1", 3);
        Path second = manager.stateDirectory("workspace-1", "task-1", 3);

        assertEquals(first, second);
        assertEquals(
                "yak-realtime-workspace-1-task-1-v3",
                manager.engineName("workspace-1", "task-1", 3));
        assertEquals("workspace-1/task-1/v3", manager.stateKey("workspace-1", "task-1", 3));
        assertTrue(first.startsWith(tempDirectory.toAbsolutePath().normalize()));
    }

    @Test
    void shouldIsolateDefinitionVersions() {
        RealtimeSyncStateNamespace manager = new RealtimeSyncStateNamespace(tempDirectory);

        assertNotEquals(
                manager.stateDirectory("workspace-1", "task-1", 1),
                manager.stateDirectory("workspace-1", "task-1", 2));
        assertNotEquals(
                manager.engineName("workspace-1", "task-1", 1),
                manager.engineName("workspace-1", "task-1", 2));
    }

    @Test
    void shouldRejectUnsafePathSegments() {
        RealtimeSyncStateNamespace manager = new RealtimeSyncStateNamespace(tempDirectory);

        assertThrows(IllegalArgumentException.class, () -> manager.stateDirectory("../workspace", "task-1", 1));
        assertThrows(IllegalArgumentException.class, () -> manager.stateDirectory("workspace-1", "../task", 1));
        assertThrows(IllegalArgumentException.class, () -> manager.stateDirectory("workspace-1", "task-1", 0));
    }
}
