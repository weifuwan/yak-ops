package io.yak.ops.business.task.instance.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.AttemptRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Verifies Workspace-scoped Instance observation and page validation. */
class InstanceServiceTest {

    private final InstanceServiceImpl service = new InstanceServiceImpl();
    private final InstanceRepository instances = mock(InstanceRepository.class);
    private final AttemptRepository attempts = mock(AttemptRepository.class);
    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);

    InstanceServiceTest() {
        inject("instances", instances);
        inject("attempts", attempts);
        inject("definitions", definitions);
        inject("versions", versions);
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
        assertThrows(BusinessException.class, () -> service.page("ws", 0, 20, null, null, null));
        assertThrows(BusinessException.class, () -> service.page("ws", 1, 201, null, null, null));
        verify(instances, never()).page("ws", 0, 20, null, null, null);
    }

    @Test
    void readsPinnedVersionAfterDefinitionDeletion() {
        InstanceEntity instance = new InstanceEntity();
        instance.setId("i");
        instance.setDefinitionId("d");
        instance.setDefinitionVersion(3);
        instance.setTaskType("DATA_SYNC");
        when(instances.findById("ws", "i")).thenReturn(Optional.of(instance));

        DefinitionEntity definition = new DefinitionEntity();
        definition.setId("d");
        definition.setName("orders");
        when(definitions.queryByIdIncludingDeleted("ws", "d")).thenReturn(Optional.of(definition));

        DefinitionVersionEntity version = new DefinitionVersionEntity();
        version.setDefinitionId("d");
        version.setVersion(3);
        version.setParameterSchemaVersion(2);
        version.setParameters("{\"source\":{\"type\":\"JDBC\"}}");
        when(versions.queryCurrent("ws", "d", 3)).thenReturn(Optional.of(version));

        var result = service.definition("ws", "i");
        assertEquals("d", result.definitionId());
        assertEquals(3, result.version());
        assertEquals("orders", result.name());
        assertEquals(2, result.parameterSchemaVersion());
        assertEquals(version.getParameters(), result.parameters());
        verify(definitions).queryByIdIncludingDeleted("ws", "d");
    }

    @Test
    void filtersInstancesWithinWorkspace() {
        service.page("ws", 1, 20, "d", TaskInstanceStatus.FINISHED, "DATA_SYNC");
        verify(instances).page("ws", 1, 20, "d", TaskInstanceStatus.FINISHED, "DATA_SYNC");
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
