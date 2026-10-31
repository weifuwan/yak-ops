package io.yak.ops.business.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.definition.impl.DefinitionServiceImpl;
import io.yak.ops.common.bean.dto.task.DefinitionQueryDTO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies Task Definition Workspace isolation and retrieval of immutable version history.
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
        definition.setCurrentVersion(5);
        when(definitions.queryById("ws", "task-1")).thenReturn(Optional.of(definition));

        DefinitionVersionEntity version = new DefinitionVersionEntity();
        version.setDefinitionId("task-1");
                version.setVersion(5);
        version.setParameters("{\"sourceTable\":\"orders\"}");
        when(versions.queryCurrent("ws", "task-1", 5)).thenReturn(Optional.of(version));
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

    @ParameterizedTest
    @ValueSource(strings = {"SINGLE_TABLE_BATCH", "DATABASE_REPLICATION"})
    void productPagePreservesWorkspaceAndExactExecutionConstraints(String profile) {
        WorkspaceContext.bind("current-workspace");
        DefinitionQueryDTO query = new DefinitionQueryDTO();
        query.setTaskType("DATA_SYNC");
        query.setPageNo(2);
        query.setPageSize(7);
        query.setKeyword(" orders ");
        query.setStatus(DefinitionStatus.PUBLISHED);
        query.setParameterFilters(Map.of("dataSyncProfile", profile, "runtimeMode", "BATCH"));
        when(definitions.queryPage(any())).thenAnswer(invocation -> {
            DefinitionPageQuery selected = invocation.getArgument(0);
            assertEquals("current-workspace", selected.workspaceId());
            assertEquals("DATA_SYNC", selected.taskType());
            assertEquals("orders", selected.keyword());
            assertEquals(1, selected.statusValue());
            assertEquals(Map.of("dataSyncProfile", profile, "runtimeMode", "BATCH"), selected.parameterFilters());
            return PageData.empty(selected.pageNo(), selected.pageSize());
        });

        var result = service.queryPage(query);
        assertEquals(List.of(), result.getBizData());
        assertEquals(2, result.getPagination().getPageNo());
        assertEquals(7, result.getPagination().getPageSize());
    }

    @ParameterizedTest
    @MethodSource("invalidProductFilters")
    void rejectsUnsupportedProductProfilesAndNonDataSyncQueries(String taskType, String profile) {
        WorkspaceContext.bind("ws");
        DefinitionQueryDTO query = new DefinitionQueryDTO();
        query.setTaskType(taskType);
        Map<String, String> filters = new HashMap<>();
        filters.put("dataSyncProfile", profile);
        query.setParameterFilters(filters);

        BusinessException rejected = assertThrows(BusinessException.class, () -> service.queryPage(query));
        assertEquals(CommonErrorCode.PARAM_NOT_VALID, rejected.getErrorCode());
    }

    private static Stream<Arguments> invalidProductFilters() {
        return Stream.of(
                Arguments.of("DATA_SYNC", "MULTI_TABLE_BATCH"),
                Arguments.of("DATA_SYNC", "UNKNOWN"),
                Arguments.of("DATA_SYNC", "DATABASE_REPLICATION' OR 1=1 --"),
                Arguments.of("DATA_SYNC", ""),
                Arguments.of("DATA_SYNC", null),
                Arguments.of("SHELL", "DATABASE_REPLICATION"),
                Arguments.of(null, "SINGLE_TABLE_BATCH"));
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
