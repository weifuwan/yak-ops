package io.yak.ops.business.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionPublicationEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.DefinitionPublicationRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.dao.repository.schedule.ScheduleRepository;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
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

    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    private final DefinitionPublicationRepository publications = mock(DefinitionPublicationRepository.class);
    private final DefinitionServiceImpl service = new DefinitionServiceImpl(
            definitions, versions, mock(TaskPluginManager.class), mock(TaskValidationContext.class),
            mock(PublicationService.class), mock(ScheduleRepository.class), mock(InstanceRepository.class), publications);

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
        definition.setPublishedVersion(5);
        definition.setPublishedDebug(false);
        definition.setRevision(8);
        when(definitions.queryById("ws", "task-1")).thenReturn(Optional.of(definition));

        DefinitionVersionEntity version = new DefinitionVersionEntity();
        version.setDefinitionId("task-1");
        version.setVersion(5);
        version.setCreateBy("editor");
        version.setParameters("{\"sourceTable\":\"orders\"}");
        when(versions.queryCurrent("ws", "task-1", 5)).thenReturn(Optional.of(version));
        when(versions.queryPage("ws", "task-1", 1, 20)).thenReturn(PageData.of(List.of(version), 1, 1, 20));

        assertEquals("PUBLISHED", service.query("task-1").getStatus());
        assertEquals(5, service.query("task-1").getDefinitionVersion());
        assertEquals(1, service.queryVersions("task-1", 1, 20).getBizData().size());
        assertEquals(5, service.queryVersions("task-1", 1, 20).getBizData().getFirst().getVersion());
        assertEquals("editor", service.queryVersions("task-1", 1, 20).getBizData().getFirst().getCreateBy());
        assertFalse(JSONUtils.toJson(service.queryVersions("task-1", 1, 20)).contains("sourceTable"));
        assertEquals(version.getParameters(), service.queryVersion("task-1", 5).getParameters());
        assertEquals("editor", service.queryVersion("task-1", 5).getCreateBy());
        assertFalse(service.query("task-1").isHasUnpublishedChanges());

        DefinitionPublicationEntity publication = new DefinitionPublicationEntity();
        publication.setId("receipt");
        publication.setDefinitionId("task-1");
        publication.setVersion(5);
        publication.setCreateBy("publisher");
        publication.setRequestId("request-1");
        when(publications.queryPage("ws", "task-1", 1, 20))
                .thenReturn(PageData.of(List.of(publication), 1, 1, 20));
        assertEquals("publisher", service.queryPublications("task-1", 1, 20).getBizData().getFirst().getCreateBy());
        assertEquals("request-1", service.queryPublications("task-1", 1, 20).getBizData().getFirst().getRequestId());
    }

    @Test
    void shouldRejectMissingWorkspaceScopedDefinition() {
        WorkspaceContext.bind("another-workspace");
        assertThrows(BusinessException.class, () -> service.query("task-1"));
        assertThrows(BusinessException.class, () -> service.queryVersions("task-1", 1, 20));
        assertThrows(BusinessException.class, () -> service.queryVersion("task-1", 5));
        assertThrows(BusinessException.class, () -> service.queryPublications("task-1", 1, 20));
    }

    @Test
    void historyPaginationIsBounded() {
        WorkspaceContext.bind("ws");
        assertEquals(CommonErrorCode.PARAM_NOT_VALID, assertThrows(BusinessException.class,
                () -> service.queryVersions("task-1", 0, 20)).getErrorCode());
        assertEquals(CommonErrorCode.PARAM_NOT_VALID, assertThrows(BusinessException.class,
                () -> service.queryPublications("task-1", 1, 201)).getErrorCode());
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

}
