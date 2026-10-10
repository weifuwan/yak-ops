package io.yak.ops.dao.repository.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.dao.entity.datasync.SyncDefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.mapper.datasync.SyncDefinitionMapper;
import io.yak.ops.dao.repository.datasync.impl.SyncDefinitionRepositoryImpl;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 验证通用 Definition 与 DATA_SYNC 专属配置分开持久化、稳定合并及版本快照。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
class SyncDefinitionRepositoryContractTest {

    private final SyncDefinitionMapper mapper = mock(SyncDefinitionMapper.class);
    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    private final SyncDefinitionRepositoryImpl repository = new SyncDefinitionRepositoryImpl();

    SyncDefinitionRepositoryContractTest() {
        inject("taskMapper", mapper);
        inject("definitionRepository", definitions);
        inject("versionRepository", versions);
    }

    @Test
    void shouldWriteOneDefinitionOneSyncConfigurationAndOneVersion() {
        SyncDefinitionEntity task = task();

        repository.add(task);

        ArgumentCaptor<DefinitionEntity> definitionCaptor = ArgumentCaptor.forClass(DefinitionEntity.class);
        ArgumentCaptor<DefinitionVersionEntity> versionCaptor = ArgumentCaptor.forClass(DefinitionVersionEntity.class);
        verify(definitions).add(definitionCaptor.capture());
        verify(mapper).insert(task);
        verify(versions).append(versionCaptor.capture());
        assertEquals("id-1", definitionCaptor.getValue().getId());
        assertEquals("DATA_SYNC", definitionCaptor.getValue().getTaskType());
        assertEquals(DefinitionStatus.UNPUBLISHED, definitionCaptor.getValue().getStatus());
        assertEquals(1, versionCaptor.getValue().getVersion());
        assertEquals("orders", JSONUtils.readTree(versionCaptor.getValue().getParametersSnapshot())
                .path("sourceTable")
                .asText());
        assertFalse(versionCaptor.getValue().getParametersSnapshot().contains("password"));
    }

    @Test
    void shouldOnlyAppendNewVersionWhenExecutableVersionChanges() {
        SyncDefinitionEntity task = task();
        DefinitionEntity previous = definition();
        when(definitions.queryById("ws-1", "id-1")).thenReturn(Optional.of(previous));
        when(definitions.update(org.mockito.ArgumentMatchers.eq("ws-1"), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(mapper.update(any(), any())).thenReturn(1);

        repository.update("ws-1", task);
        verify(versions, never()).append(any());

        task.setDefinitionVersion(2);
        repository.update("ws-1", task);
        ArgumentCaptor<DefinitionVersionEntity> versionCaptor = ArgumentCaptor.forClass(DefinitionVersionEntity.class);
        verify(versions).append(versionCaptor.capture());
        assertEquals(2, versionCaptor.getValue().getVersion());
        assertEquals("id-1", versionCaptor.getValue().getDefinitionId());
    }

    @Test
    void shouldUseDefinitionStatusAndNameForPaginatedReads() {
        Page<String> ids = Page.of(1, 10);
        ids.setRecords(List.of("id-1"));
        ids.setTotal(1);
        when(mapper.selectDefinitionPageIds(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ids);
        when(mapper.selectBatchIds(List.of("id-1"))).thenReturn(List.of(task()));
        when(definitions.queryByIds("ws-1", List.of("id-1"))).thenReturn(List.of(definition()));

        PageData<SyncDefinitionEntity> page = repository.queryPage(
                "ws-1", new SyncDefinitionPageQuery(1, 10, null, DataSyncType.OFFLINE, null, null, null));

        assertEquals(1, page.total());
        assertEquals("同步订单", page.records().getFirst().getName());
        assertEquals(DataSyncTaskStatus.UNPUBLISHED, page.records().getFirst().getStatus());
        assertEquals(1, page.records().getFirst().getDefinitionVersion());
    }

    @Test
    void shouldHideDefinitionsFromOtherWorkspacesAndTypes() {
        when(definitions.queryById("another", "id-1")).thenReturn(Optional.empty());
        assertTrue(repository.queryById("another", "id-1").isEmpty());

        DefinitionEntity sqlDefinition = definition();
        sqlDefinition.setTaskType("SQL");
        when(definitions.queryById("ws-1", "id-1")).thenReturn(Optional.of(sqlDefinition));
        assertTrue(repository.queryById("ws-1", "id-1").isEmpty());
        verify(mapper, never()).selectOne(any());
    }

    private SyncDefinitionEntity task() {
        SyncDefinitionEntity task = new SyncDefinitionEntity();
        task.setId("id-1");
        task.setWorkspaceId("ws-1");
        task.setName("同步订单");
        task.setStatus(DataSyncTaskStatus.UNPUBLISHED);
        task.setSyncType(DataSyncType.OFFLINE);
        task.setDesiredState(DataSyncDesiredState.STOPPED);
        task.setWriteMode(DataSyncWriteMode.APPEND);
        task.setDefinitionVersion(1);
        task.setSourceDataSourceId("source");
        task.setSourceTable("orders");
        task.setTargetDataSourceId("target");
        task.setTargetTable("orders_copy");
        task.setRuntimeConfig("{}");
        task.setRetryPolicy("{}");
        task.initUpdate("test-user");
        return task;
    }

    private DefinitionEntity definition() {
        DefinitionEntity definition = new DefinitionEntity();
        definition.setId("id-1");
        definition.setWorkspaceId("ws-1");
        definition.setName("同步订单");
        definition.setTaskType("DATA_SYNC");
        definition.setStatus(DefinitionStatus.UNPUBLISHED);
        definition.setDefinitionVersion(1);
        return definition;
    }

    private void inject(String name, Object dependency) {
        try {
            Field field = SyncDefinitionRepositoryImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(repository, dependency);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
