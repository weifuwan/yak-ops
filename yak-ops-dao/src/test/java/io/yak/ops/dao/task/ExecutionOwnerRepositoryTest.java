package io.yak.ops.dao.task;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.repository.task.impl.ExecutionOwnerRepositoryImpl;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies embedded owner identity checks before replacing a recorded incarnation. */
class ExecutionOwnerRepositoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ExecutionOwnerRepositoryImpl repository = new ExecutionOwnerRepositoryImpl();

    ExecutionOwnerRepositoryTest() {
        try {
            Field field = ExecutionOwnerRepositoryImpl.class.getDeclaredField("jdbc");
            field.setAccessible(true);
            field.set(repository, jdbc);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Test
    void rejectsDifferentHostBeforeUpdatingTheOwnerRecord() {
        when(jdbc.queryForList(anyString(), eq("embedded")))
                .thenReturn(List.of(Map.of("host_id", "other-host", "state_root", "/state")));

        assertThrows(IllegalStateException.class, () -> repository.register("host", "/state", "incarnation"));

        verify(jdbc).queryForList(anyString(), eq("embedded"));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void recordsInitialOwnerWhenTheTableHasNoOwner() {
        when(jdbc.queryForList(anyString(), eq("embedded"))).thenReturn(List.of());

        repository.register("host", "/state", "incarnation");

        verify(jdbc).queryForList(anyString(), eq("embedded"));
        verify(jdbc).update(
                org.mockito.ArgumentMatchers.startsWith("INSERT INTO yak_ops_task_execution_owner"),
                eq("embedded"),
                eq("host"),
                eq("/state"),
                eq("incarnation"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
