package io.yak.ops.business.schedule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.common.bean.dto.schedule.CreateScheduleDTO;
import io.yak.ops.common.bean.dto.schedule.SchedulePreviewDTO;
import io.yak.ops.common.bean.dto.schedule.ScheduleQueryDTO;
import io.yak.ops.common.bean.dto.schedule.UpdateScheduleDTO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.schedule.ScheduleErrorCode;
import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.dao.entity.schedule.ScheduleEntity;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.repository.schedule.ScheduleRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.plugin.scheduler.api.CronService;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tests configuration ownership and lock order without claiming database concurrency acceptance. */
class ScheduleServiceTest {
    private final ScheduleServiceImpl service = new ScheduleServiceImpl();
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    private final TaskPluginRegistry plugins = mock(TaskPluginRegistry.class);
    private final ExecutableTaskPlugin plugin = mock(ExecutableTaskPlugin.class);
    private final CronService cron = mock(CronService.class);
    private final DefinitionEntity definition = new DefinitionEntity();
    private final ScheduleEntity schedule = new ScheduleEntity();

    @BeforeEach
    void configure() throws Exception {
        for (var entry : Map.of(
                        "schedules",
                        schedules,
                        "definitions",
                        definitions,
                        "versions",
                        versions,
                        "plugins",
                        plugins,
                        "cron",
                        cron)
                .entrySet()) {
            var field = ScheduleServiceImpl.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(service, entry.getValue());
        }
        definition.setId("task");
        definition.setWorkspaceId("ws");
        definition.setTaskType("TEST");
        definition.setCurrentVersion(1);
        definition.setStatus(DefinitionStatus.PUBLISHED);
        when(definitions.queryByIdForUpdate("ws", "task")).thenReturn(Optional.of(definition));
        when(definitions.queryByIdIncludingDeleted("ws", "task")).thenReturn(Optional.of(definition));
        when(definitions.queryByIdIncludingDeletedForUpdate("ws", "task")).thenReturn(Optional.of(definition));
        DefinitionVersionEntity version = new DefinitionVersionEntity();
        version.setParameters("{}");
        when(versions.queryCurrent("ws", "task", 1)).thenReturn(Optional.of(version));
        when(plugins.get("TEST")).thenReturn(plugin);
        when(plugin.parse("{}")).thenReturn(ReadonlyConfig.fromMap(Map.of()));
        when(plugin.supportsScheduling(any())).thenReturn(true);
        schedule.setId("schedule");
        schedule.setWorkspaceId("ws");
        schedule.setTargetId("task");
        schedule.setTargetType(ScheduleTargetType.TASK);
        schedule.setRevision(1L);
        schedule.setEnabled(false);
        schedule.setCronExpression("0 * * * * ?");
        schedule.setTimeZone("UTC");
        when(schedules.queryById("ws", "schedule")).thenReturn(Optional.of(schedule));
        when(schedules.queryByIdForUpdate("ws", "schedule")).thenReturn(Optional.of(schedule));
        when(schedules.updateDisabled(any(), eq(1L))).thenReturn(true);
        when(schedules.softDelete(eq("ws"), eq("schedule"), eq(1L), any(), eq("actor")))
                .thenReturn(true);
    }

    @Test
    void createsDisabledConfigurationAndNeverOpensResources() {
        var result = service.create("ws", create(), "actor");
        assertFalse(result.isEnabled());
        assertEquals(1, result.getRevision());
        assertTrue(result.isSchedulable());
        assertEquals("PENDING", result.getRegistrationStatus());
        ArgumentCaptor<ScheduleEntity> captured = ArgumentCaptor.forClass(ScheduleEntity.class);
        verify(schedules).add(captured.capture());
        assertNotNull(captured.getValue().getId());
        assertEquals("ws", captured.getValue().getWorkspaceId());
        assertEquals("actor", captured.getValue().getCreateBy());
        verify(plugin, never()).validate(any(), any());
        verify(plugin, never()).plan(any(), any());
    }

    @Test
    void allowsSavingUnpublishedAndManualOnlyTargetsWithoutEnablingThem() {
        definition.setStatus(DefinitionStatus.UNPUBLISHED);
        assertEquals(
                "TARGET_UNPUBLISHED", service.create("ws", create(), "actor").getEligibilityReason());
        definition.setStatus(DefinitionStatus.PUBLISHED);
        when(plugin.supportsScheduling(any())).thenReturn(false);
        assertEquals("NOT_SCHEDULABLE", service.create("ws", create(), "actor").getEligibilityReason());
    }

    @Test
    void duplicateTargetAndOtherWorkspaceAreRejectedBeforeInsert() {
        when(schedules.queryByTarget("ws", ScheduleTargetType.TASK, "task")).thenReturn(Optional.of(schedule));
        assertEquals(
                ScheduleErrorCode.TARGET_ALREADY_SCHEDULED,
                assertThrows(BusinessException.class, () -> service.create("ws", create(), "actor"))
                        .getErrorCode());
        assertEquals(
                CommonErrorCode.RESOURCE_NOT_EXISTS,
                assertThrows(BusinessException.class, () -> service.create("other", create(), "actor"))
                        .getErrorCode());
        assertThrows(BusinessException.class, () -> service.query("other", "schedule"));
        verify(schedules, never()).add(any());
    }

    @Test
    void editLocksDefinitionBeforeScheduleAndIncrementsRevision() {
        var result = service.update("ws", "schedule", update("0 0 8 * * ?", 1), "actor");
        assertEquals(2, result.getRevision());
        assertFalse(result.isEnabled());
        var order = inOrder(definitions, schedules);
        order.verify(schedules).queryById("ws", "schedule");
        order.verify(definitions).queryByIdForUpdate("ws", "task");
        order.verify(schedules).queryByIdForUpdate("ws", "schedule");
        order.verify(schedules).updateDisabled(any(), eq(1L));
    }

    @Test
    void unchangedRuleDoesNotBumpRevisionAndStaleEditCannotOverwrite() {
        assertEquals(
                1,
                service.update("ws", "schedule", update("0 * * * * ?", 1), "actor")
                        .getRevision());
        assertEquals(
                ScheduleErrorCode.REVISION_CONFLICT,
                assertThrows(
                                BusinessException.class,
                                () -> service.update("ws", "schedule", update("0 0 8 * * ?", 2), "actor"))
                        .getErrorCode());
        verify(schedules, never()).updateDisabled(any(), anyLong());
    }

    @Test
    void editRequiresDisabledStateAndNeverChangesTargetOrEnablement() {
        schedule.setEnabled(true);
        assertEquals(
                ScheduleErrorCode.MUST_BE_DISABLED,
                assertThrows(
                                BusinessException.class,
                                () -> service.update("ws", "schedule", update("0 0 8 * * ?", 1), "actor"))
                        .getErrorCode());
        assertEquals("task", schedule.getTargetId());
        assertEquals(1, schedule.getRevision());
    }

    @Test
    void deleteLocksRetainedDefinitionBeforeScheduleAndKeepsHistory() {
        service.delete("ws", "schedule", 1, "actor");
        var order = inOrder(definitions, schedules);
        order.verify(schedules).queryById("ws", "schedule");
        order.verify(definitions).queryByIdIncludingDeletedForUpdate("ws", "task");
        order.verify(schedules).queryByIdForUpdate("ws", "schedule");
        order.verify(schedules).softDelete(eq("ws"), eq("schedule"), eq(1L), any(), eq("actor"));
    }

    @Test
    void previewDoesNotRequireSavedTargetAndDoesNotResolveResources() {
        SchedulePreviewDTO dto = new SchedulePreviewDTO();
        dto.setCronExpression("0 * * * * ?");
        dto.setTimeZone("UTC");
        Instant after = Instant.parse("2026-10-13T00:00:00Z");
        dto.setAfter(after);
        when(cron.nextFireTimes(dto.getCronExpression(), "UTC", after, 5)).thenReturn(List.of(after.plusSeconds(60)));
        assertEquals(List.of(after.plusSeconds(60)), service.preview(dto).nextFireTimes());
        verify(schedules, never()).add(any());
        verify(definitions, never()).queryByIdForUpdate(any(), any());
    }

    @Test
    void invalidRulesAreLocalizedWithoutEngineDetails() {
        doThrow(new IllegalArgumentException("engine diagnostic")).when(cron).validate(any(), any());
        BusinessException error = assertThrows(BusinessException.class, () -> service.create("ws", create(), "actor"));
        assertEquals(ScheduleErrorCode.INVALID_TIME_RULE, error.getErrorCode());
        assertFalse(error.toString().contains("engine diagnostic"));
        verify(schedules, never()).add(any());
    }

    @Test
    void pageUsesOneScopedRepositorySelectionWithoutPerRowEligibilityQueries() {
        when(schedules.queryPage(any())).thenReturn(PageData.of(List.of(schedule), 1, 1, 20));
        var page = service.queryPage("ws", new ScheduleQueryDTO());
        assertEquals(1, page.total());
        assertEquals("PENDING", page.records().getFirst().getRegistrationStatus());
        verify(versions, never()).queryCurrent(any(), any(), anyInt());
    }

    private CreateScheduleDTO create() {
        CreateScheduleDTO dto = new CreateScheduleDTO();
        dto.setTargetType(ScheduleTargetType.TASK);
        dto.setTargetId("task");
        dto.setCronExpression("0 * * * * ?");
        dto.setTimeZone("UTC");
        return dto;
    }

    private UpdateScheduleDTO update(String expression, long revision) {
        UpdateScheduleDTO dto = new UpdateScheduleDTO();
        dto.setCronExpression(expression);
        dto.setTimeZone("UTC");
        dto.setRevision(revision);
        return dto;
    }
}
