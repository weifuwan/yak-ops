package io.yak.ops.business.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.definition.impl.DefinitionServiceImpl;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 通用 Task Definition 的 Workspace 隔离与真实历史版本查询合同。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
class DefinitionServiceContractTest {

    private final DefinitionServiceImpl service = new DefinitionServiceImpl();
    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);

    DefinitionServiceContractTest() {
        inject("definitionRepository", definitions);
        inject("versionRepository", versions);
    }

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldReturnCanonicalDefinitionAndPersistedVersions() {
        WorkspaceContext.bind("ws");
        DefinitionEntity definition = new DefinitionEntity();
        definition.setId("task-1");
        definition.setWorkspaceId("ws");
        definition.setName("同步订单");
        definition.setTaskType("DATA_SYNC");
        definition.setStatus(DefinitionStatus.PUBLISHED);
        definition.setDefinitionVersion(5);
        when(definitions.queryById("ws", "task-1")).thenReturn(Optional.of(definition));

        DefinitionVersionEntity version = new DefinitionVersionEntity();
        version.setDefinitionId("task-1");
        version.setTaskType("DATA_SYNC");
        version.setVersion(5);
        version.setParametersSnapshot("{\"sourceTable\":\"orders\"}");
        when(versions.queryByDefinition("ws", "task-1")).thenReturn(List.of(version));

        assertEquals("PUBLISHED", service.query("task-1").getStatus());
        assertEquals(5, service.query("task-1").getDefinitionVersion());
        assertEquals(1, service.queryVersions("task-1").size());
        assertEquals(5, service.queryVersions("task-1").getFirst().getVersion());
    }

    @Test
    void shouldRejectMissingWorkspaceScopedDefinition() {
        WorkspaceContext.bind("another-workspace");
        assertThrows(BusinessException.class, () -> service.query("task-1"));
        assertThrows(BusinessException.class, () -> service.queryVersions("task-1"));
    }

    private void inject(String name, Object dependency) {
        try {
            Field field = DefinitionServiceImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, dependency);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
