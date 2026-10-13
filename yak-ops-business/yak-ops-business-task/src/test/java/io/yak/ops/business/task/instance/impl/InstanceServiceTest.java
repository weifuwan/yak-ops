package io.yak.ops.business.task.instance.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.AttemptRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Verifies Workspace-scoped Instance observation and page validation. */
class InstanceServiceTest {

    private final InstanceServiceImpl service = new InstanceServiceImpl();
    private final InstanceRepository instances = mock(InstanceRepository.class);
    private final AttemptRepository attempts = mock(AttemptRepository.class);

    InstanceServiceTest() {
        inject("instances", instances);
        inject("attempts", attempts);
    }

    @Test
    void readsAttemptsOnlyAfterInstanceOwnershipCheck() {
        when(instances.findById("ws", "i")).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.attempts("ws", "i"));
        verify(attempts, never()).findByInstance("ws", "i");
    }

    @Test
    void readsOwnedInstance() {
        InstanceEntity instance = new InstanceEntity();
        instance.setId("i");
        when(instances.findById("ws", "i")).thenReturn(Optional.of(instance));
        assertEquals(instance, service.instance("ws", "i"));
    }

    @Test
    void rejectsInvalidPageBeforeQuery() {
        assertThrows(BusinessException.class, () -> service.page("ws", 0, 20));
        assertThrows(BusinessException.class, () -> service.page("ws", 1, 201));
        verify(instances, never()).page("ws", 0, 20);
    }

    private void inject(String name, Object value) {
        try {
            Field field = InstanceServiceImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
