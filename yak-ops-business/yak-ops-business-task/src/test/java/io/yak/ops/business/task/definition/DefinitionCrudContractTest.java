package io.yak.ops.business.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.definition.impl.DefinitionServiceImpl;
import io.yak.ops.business.task.definition.impl.PublicationServiceImpl;
import io.yak.ops.common.bean.dto.task.CreateDefinitionDTO;
import io.yak.ops.common.bean.dto.task.PublishDefinitionDTO;
import io.yak.ops.common.bean.dto.task.UpdateDefinitionDTO;
import io.yak.ops.common.bean.vo.task.DefinitionVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import io.yak.ops.common.enums.task.DefinitionErrorCode;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionPublicationEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.schedule.ScheduleRepository;
import io.yak.ops.dao.repository.task.DefinitionPublicationRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Saved versions and publication evolve independently while execution and history stay fixed. */
class DefinitionCrudContractTest {
    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    private final DefinitionPublicationRepository publications = mock(DefinitionPublicationRepository.class);
    private final TaskPluginManager registry = mock(TaskPluginManager.class);
    private final TaskValidationContext context = mock(TaskValidationContext.class);
    private final TaskChannel plugin = mock(TaskChannel.class);
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final InstanceRepository instances = mock(InstanceRepository.class);
    private final DefinitionServiceImpl service = new DefinitionServiceImpl(
            definitions, versions, registry, context,
            new PublicationServiceImpl(definitions, publications, schedules),
            schedules, instances, publications);
    private final Map<Integer, DefinitionVersionEntity> snapshots = new HashMap<>();
    private final Map<String, DefinitionPublicationEntity> receipts = new HashMap<>();
    private DefinitionEntity stored;

    DefinitionCrudContractTest() {
        when(plugin.parameterSchemaVersion()).thenReturn(1);
        when(plugin.supportsScheduling(any())).thenReturn(true);
        when(registry.get("DATA_SYNC")).thenReturn(plugin);
        when(plugin.parse(anyString())).thenAnswer(args -> parameters(args.getArgument(0)));
        when(plugin.parse(anyInt(), anyString())).thenAnswer(args -> parameters(args.getArgument(1)));
        when(definitions.add(any())).thenAnswer(args -> {
            stored = args.getArgument(0);
            return stored;
        });
        when(definitions.queryByIdForUpdate(eq("ws"), anyString())).thenAnswer(args -> Optional.ofNullable(stored));
        when(definitions.queryById(eq("ws"), anyString())).thenAnswer(args -> Optional.ofNullable(stored));
        when(definitions.update(eq("ws"), any())).thenAnswer(args -> args.getArgument(1));
        when(versions.append(any())).thenAnswer(args -> {
            DefinitionVersionEntity version = args.getArgument(0);
            snapshots.put(version.getVersion(), version);
            return version;
        });
        when(versions.queryCurrent(eq("ws"), anyString(), anyInt()))
                .thenAnswer(args -> Optional.ofNullable(snapshots.get(args.getArgument(2))));
        when(publications.findByRequest(eq("ws"), anyString(), anyString()))
                .thenAnswer(args -> Optional.ofNullable(receipts.get(args.getArgument(2))));
        doAnswer(args -> {
            DefinitionPublicationEntity receipt = args.getArgument(0);
            receipts.put(receipt.getRequestId(), receipt);
            return null;
        }).when(publications).append(any());
    }

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void savingRunningPublishedTaskKeepsPublicationAndHistoricalParameters() {
        create();
        DefinitionVO published = service.publish(stored.getId(), publication("publish-1"), "publisher");
        assertEquals(1, published.getPublishedVersion());
        assertFalse(published.getPublishedDebug());
        assertFalse(published.isHasUnpublishedChanges());
        when(instances.latest("ws", List.of(stored.getId()), true)).thenReturn(List.of(new InstanceEntity()));

        UpdateDefinitionDTO edit = edit("orders renamed", "orders_new");
        edit.setDebug(true);
        DefinitionVO saved = service.update(stored.getId(), edit, "editor");

        assertEquals("PUBLISHED", saved.getStatus());
        assertEquals(2, saved.getDefinitionVersion());
        assertEquals(1, saved.getPublishedVersion());
        assertFalse(saved.getPublishedDebug());
        assertTrue(saved.isDebug());
        assertTrue(saved.isHasUnpublishedChanges());
        assertEquals(3, saved.getRevision());
        assertEquals("orders", JSONUtils.readTree(snapshots.get(1).getParameters()).path("sourceTable").asText());
        assertEquals(2, snapshots.size());
        assertEquals(1, receipts.size());

        DefinitionVO next = service.publish(stored.getId(), publication("publish-2"), "publisher");
        assertEquals(2, next.getPublishedVersion());
        assertTrue(next.getPublishedDebug());
        assertFalse(next.isHasUnpublishedChanges());
        assertEquals(2, snapshots.size());
    }

    @Test
    void metadataAndDebugChangesDoNotCreateParameterVersions() {
        create();
        service.publish(stored.getId(), publication("publish-1"), "publisher");
        UpdateDefinitionDTO rename = edit("orders renamed", null);
        rename.setRemark("description");
        DefinitionVO metadata = service.update(stored.getId(), rename, "editor");
        assertFalse(metadata.isHasUnpublishedChanges());

        UpdateDefinitionDTO debug = edit("orders renamed", null);
        debug.setDebug(true);
        DefinitionVO saved = service.update(stored.getId(), debug, "editor");
        assertEquals(1, saved.getDefinitionVersion());
        assertTrue(saved.isHasUnpublishedChanges());
        assertFalse(saved.getPublishedDebug());
        DefinitionVO published = service.publish(stored.getId(), publication("publish-2"), "publisher");
        assertTrue(published.getPublishedDebug());
        assertFalse(published.isHasUnpublishedChanges());
        assertEquals(1, snapshots.size());
        assertEquals(2, receipts.size());
        assertEquals(1, receipts.get("publish-2").getVersion());
    }

    @Test
    void staleEditorCannotOverwriteSavedMetadataOrParameters() {
        create();
        UpdateDefinitionDTO old = edit("stale", "stale");
        service.update(stored.getId(), edit("newer", "newer"), "editor-a");
        BusinessException error = assertThrows(
                BusinessException.class, () -> service.update(stored.getId(), old, "editor-b"));
        assertEquals(DefinitionErrorCode.REVISION_CONFLICT, error.getErrorCode());
        assertEquals("newer", stored.getName());
        assertEquals(2, snapshots.size());
        assertEquals(2, stored.getRevision());
        old.setExpectedRevision(null);
        assertEquals(CommonErrorCode.PARAM_NOT_VALID, assertThrows(
                BusinessException.class, () -> service.update(stored.getId(), old, "editor-b")).getErrorCode());
    }

    @Test
    void equivalentJsonDoesNotCreateAnotherVersion() {
        create();
        UpdateDefinitionDTO formatted = edit("orders", null);
        formatted.setParameters("{ \"sourceTable\" : \"orders\" }");
        assertEquals(1, service.update(stored.getId(), formatted, "editor").getDefinitionVersion());
        assertEquals(1, snapshots.size());
        assertEquals(2, stored.getRevision());
    }

    @Test
    void unchangedPayloadUnderNewParameterSchemaCreatesOnlyOneNewSavedVersion() {
        when(plugin.parameterSchemaVersion()).thenReturn(7);
        create();
        service.publish(stored.getId(), publication("publish-v7"), "publisher");
        String historical = snapshots.get(1).getParameters();

        when(plugin.parameterSchemaVersion()).thenReturn(8);
        DefinitionVO saved = service.update(stored.getId(), edit("orders", "orders"), "editor");
        assertEquals(2, saved.getDefinitionVersion());
        assertEquals(8, saved.getParameterSchemaVersion());
        assertEquals(1, saved.getPublishedVersion());
        assertTrue(saved.isHasUnpublishedChanges());
        assertEquals(7, snapshots.get(1).getParameterSchemaVersion());
        assertEquals(historical, snapshots.get(1).getParameters());
        assertEquals(8, snapshots.get(2).getParameterSchemaVersion());

        DefinitionVO repeated = service.update(stored.getId(), edit("orders", "orders"), "editor");
        assertEquals(2, repeated.getDefinitionVersion());
        assertEquals(2, snapshots.size());
        assertEquals(1, receipts.size());
    }

    @Test
    void successfulPublishRetryDoesNotRevalidateOrChangeLaterEdits() {
        create();
        PublishDefinitionDTO request = publication("publish-1");
        service.publish(stored.getId(), request, "publisher");
        service.update(stored.getId(), edit("newer", "newer"), "editor");
        doThrow(new TaskPluginException("private-resource-detail")).when(plugin).validate(any(), any());

        DefinitionVO replay = service.publish(stored.getId(), request, "publisher");
        assertEquals(3, replay.getRevision());
        assertEquals(2, replay.getDefinitionVersion());
        assertEquals(1, replay.getPublishedVersion());
        assertEquals(1, receipts.size());
        request.setVersion(2);
        assertEquals(DefinitionErrorCode.PUBLICATION_REQUEST_CONFLICT, assertThrows(
                BusinessException.class, () -> service.publish(stored.getId(), request, "publisher")).getErrorCode());
    }

    @Test
    void failedPublicationKeepsSavedVersionAndCurrentPublication() {
        create();
        service.publish(stored.getId(), publication("publish-1"), "publisher");
        service.update(stored.getId(), edit("newer", "newer"), "editor");
        TaskPluginException cause = new TaskPluginException("private-resource-detail");
        doThrow(cause).when(plugin).validate(any(), any());

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.publish(stored.getId(), publication("publish-2"), "publisher"));
        assertEquals(DefinitionErrorCode.PUBLICATION_INVALID, error.getErrorCode());
        assertSame(cause, error.getCause());
        assertFalse(error.getMessage().contains("private-resource-detail"));
        assertEquals(1, stored.getPublishedVersion());
        assertEquals(2, stored.getCurrentVersion());
        assertEquals(3, stored.getRevision());
        assertEquals(1, receipts.size());
    }

    @Test
    void selectedHistoricalVersionDoesNotRewriteLatestSavedParameters() {
        create();
        service.update(stored.getId(), edit("orders", "newer"), "editor");
        PublishDefinitionDTO selected = publication("publish-1");
        selected.setVersion(1);
        DefinitionVO result = service.publish(stored.getId(), selected, "publisher");
        assertEquals(1, result.getPublishedVersion());
        assertEquals(2, result.getDefinitionVersion());
        assertTrue(result.isHasUnpublishedChanges());
        assertEquals("newer", JSONUtils.readTree(result.getParameters()).path("sourceTable").asText());
    }

    @Test
    void unpublishAndDeleteStillRequireNoActiveInstances() {
        create();
        service.publish(stored.getId(), publication("publish-1"), "publisher");
        when(instances.latest("ws", List.of(stored.getId()), true)).thenReturn(List.of(new InstanceEntity()));
        assertEquals(CommonErrorCode.TASK_INSTANCE_ACTIVE, assertThrows(
                BusinessException.class, () -> service.unpublish(stored.getId(), "publisher")).getErrorCode());
        assertEquals(DefinitionStatus.PUBLISHED, stored.getStatus());
        assertEquals(1, stored.getPublishedVersion());

        when(instances.latest("ws", List.of(stored.getId()), true)).thenReturn(List.of());
        DefinitionVO result = service.unpublish(stored.getId(), "publisher");
        assertNull(result.getPublishedVersion());
        assertNull(result.getPublishedDebug());
        assertEquals("UNPUBLISHED", result.getStatus());
        assertEquals(3, result.getRevision());
        verify(schedules).disableForTarget(eq("ws"), eq(ScheduleTargetType.TASK), eq(stored.getId()), any(), eq("publisher"));
        when(instances.latest("ws", List.of(stored.getId()), true)).thenReturn(List.of(new InstanceEntity()));
        assertEquals(CommonErrorCode.TASK_INSTANCE_ACTIVE, assertThrows(
                BusinessException.class, () -> service.delete(stored.getId(), "publisher")).getErrorCode());
    }

    private void create() {
        WorkspaceContext.bind("ws");
        CreateDefinitionDTO dto = new CreateDefinitionDTO();
        dto.setName("orders");
        dto.setTaskType("DATA_SYNC");
        dto.setParameters("{\"sourceTable\":\"orders\"}");
        DefinitionVO created = service.create(dto, "creator");
        assertEquals(1, created.getRevision());
        assertNull(created.getPublishedVersion());
        assertEquals("creator", snapshots.get(1).getCreateBy());
    }

    private UpdateDefinitionDTO edit(String name, String table) {
        UpdateDefinitionDTO dto = new UpdateDefinitionDTO();
        dto.setName(name);
        dto.setExpectedRevision(stored.getRevision());
        if (table != null) dto.setParameters(JSONUtils.toJson(Map.of("sourceTable", table)));
        return dto;
    }

    private PublishDefinitionDTO publication(String requestId) {
        PublishDefinitionDTO dto = new PublishDefinitionDTO();
        dto.setVersion(stored.getCurrentVersion());
        dto.setExpectedRevision(stored.getRevision());
        dto.setRequestId(requestId);
        return dto;
    }

    private static ReadonlyConfig parameters(String json) {
        return ReadonlyConfig.fromMap(Map.of("sourceTable", JSONUtils.readTree(json).path("sourceTable").asText()));
    }
}
