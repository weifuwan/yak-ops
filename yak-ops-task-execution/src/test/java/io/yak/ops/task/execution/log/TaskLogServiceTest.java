package io.yak.ops.task.execution.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.instance.InstanceService;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.InstanceRepository;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies Workspace isolation, ordered archives, paging and safe Task log file paths. */
class TaskLogServiceTest {

    @TempDir
    Path directory;

    private final InstanceService instances = mock(InstanceService.class);
    private final TaskLogService logs = new TaskLogService();
    private final InstanceRepository repository = mock(InstanceRepository.class);

    TaskLogServiceTest() {
        inject("instances", instances);
        inject("repository", repository);
    }

    @Test
    void readsArchiveAndActiveLogWithIncrementalCursor() throws Exception {
        inject("directory", directory.toString());
        InstanceEntity instance = instance("task-one");
        when(instances.instance("workspace", "task-one")).thenReturn(instance);
        Path task = Files.createDirectories(directory.resolve("task-one"));
        try (GZIPOutputStream compressed =
                new GZIPOutputStream(Files.newOutputStream(task.resolve("task.2026-10-13.0.log.gz")))) {
            compressed.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        Path active = task.resolve("task.log");
        Files.writeString(active, "third\n");

        var first = logs.read("workspace", "task-one", null, 2);
        assertEquals("first\nsecond\n", first.content());
        assertTrue(first.hasMore());
        assertEquals("Mg", first.nextCursor());

        var next = logs.read("workspace", "task-one", first.nextCursor(), 2);
        assertEquals("third\n", next.content());
        assertFalse(next.hasMore());

        Files.writeString(active, "fourth\n", StandardOpenOption.APPEND);
        var added = logs.read("workspace", "task-one", next.nextCursor(), 2);
        assertEquals("fourth\n", added.content());

        ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
        logs.download("workspace", "task-one", downloaded);
        assertEquals("first\nsecond\nthird\nfourth\n", downloaded.toString(StandardCharsets.UTF_8));
    }

    @Test
    void missingLogAndInvalidCursorHaveSeparateOutcomes() throws Exception {
        inject("directory", directory.toString());
        when(instances.instance("workspace", "task-one")).thenReturn(instance("task-one"));
        assertEquals("", logs.read("workspace", "task-one", null, 200).content());
        assertThrows(IllegalArgumentException.class, () -> logs.read("workspace", "task-one", "oops", 200));
        assertThrows(IllegalArgumentException.class, () -> logs.read("workspace", "task-one", null, 501));
        assertThrows(IllegalArgumentException.class, () -> logs.read("workspace", "task-one", null, 0));

        InstanceEntity historical = instance("old");
        historical.setLogPath(null);
        when(instances.instance("workspace", "old")).thenReturn(historical);
        assertEquals("", logs.read("workspace", "old", null, 200).content());
    }

    @Test
    void rejectsWorkspaceAndFileSystemPathEscapes() throws Exception {
        inject("directory", directory.toString());
        when(instances.instance("foreign", "task-one")).thenThrow(new IllegalStateException("Not owned"));
        assertThrows(IllegalStateException.class, () -> logs.read("foreign", "task-one", null, 200));

        InstanceEntity instance = instance("task-one");
        instance.setLogPath("tasks/../private");
        when(instances.instance("workspace", "task-one")).thenReturn(instance);
        assertThrows(IllegalStateException.class, () -> logs.read("workspace", "task-one", null, 200));

        instance.setLogPath("tasks/task-one/task.log");
        Files.createSymbolicLink(directory.resolve("task-one"), Files.createDirectories(directory.resolve("elsewhere")));
        assertThrows(IllegalStateException.class, () -> logs.read("workspace", "task-one", null, 200));
    }

    @Test
    void removesExpiredTerminalLogsWithoutDeletingActiveInstances() throws Exception {
        inject("directory", directory.toString());
        inject("retentionDays", 7);
        InstanceEntity finished = instance("finished");
        finished.setStatus(TaskInstanceStatus.FINISHED);
        InstanceEntity running = instance("running");
        running.setStatus(TaskInstanceStatus.RUNNING);
        when(repository.findByIdForLogCleanup("finished")).thenReturn(Optional.of(finished));
        when(repository.findByIdForLogCleanup("running")).thenReturn(Optional.of(running));

        Path completedLog = Files.createDirectories(directory.resolve("finished")).resolve("task.log");
        Path activeLog = Files.createDirectories(directory.resolve("running")).resolve("task.log");
        Files.writeString(completedLog, "done\n");
        Files.writeString(activeLog, "active\n");
        FileTime expired = FileTime.from(Instant.now().minus(Duration.ofDays(9)));
        Files.setLastModifiedTime(completedLog, expired);
        Files.setLastModifiedTime(activeLog, expired);

        logs.cleanupExpired();
        assertFalse(Files.exists(completedLog));
        assertTrue(Files.exists(activeLog));
    }

    private static InstanceEntity instance(String id) {
        InstanceEntity result = new InstanceEntity();
        result.setId(id);
        result.setWorkspaceId("workspace");
        result.setLogPath("tasks/" + id + "/task.log");
        return result;
    }

    private void inject(String field, Object value) {
        try {
            Field target = TaskLogService.class.getDeclaredField(field);
            target.setAccessible(true);
            target.set(logs, value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
