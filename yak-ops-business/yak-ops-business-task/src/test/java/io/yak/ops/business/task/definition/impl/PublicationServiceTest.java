package io.yak.ops.business.task.definition.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import io.yak.ops.common.enums.task.DefinitionErrorCode;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.schedule.ScheduleEntity;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionPublicationEntity;
import io.yak.ops.dao.repository.schedule.ScheduleRepository;
import io.yak.ops.dao.repository.task.DefinitionPublicationRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/** Publication guards stale validation, scheduling eligibility and retry identity. */
class PublicationServiceTest {
    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final DefinitionPublicationRepository publications = mock(DefinitionPublicationRepository.class);
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final PublicationServiceImpl service = new PublicationServiceImpl(definitions, publications, schedules);
    private final DefinitionEntity definition = new DefinitionEntity();

    @BeforeEach
    void prepareDefinition() {
        WorkspaceContext.bind("ws");
        definition.setId("def");
        definition.setWorkspaceId("ws");
        definition.setCurrentVersion(3);
        definition.setRevision(7);
        definition.setStatus(DefinitionStatus.PUBLISHED);
        definition.setPublishedVersion(2);
        definition.setPublishedDebug(false);
        definition.setDebug(true);
        when(definitions.queryByIdForUpdate("ws", "def")).thenReturn(Optional.of(definition));
        when(definitions.update(eq("ws"), any())).thenReturn(definition);
    }

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void publishesSelectedVersionAndDiagnosticsWithOneAuditedReceipt() {
        service.publish("def", 3, 7, "request-1", true, true, "publisher");
        assertEquals(3, definition.getPublishedVersion());
        assertTrue(definition.getPublishedDebug());
        assertEquals(8, definition.getRevision());
        ArgumentCaptor<DefinitionPublicationEntity> receipt = ArgumentCaptor.forClass(DefinitionPublicationEntity.class);
        verify(publications).append(receipt.capture());
        assertEquals("ws", receipt.getValue().getWorkspaceId());
        assertEquals("def", receipt.getValue().getDefinitionId());
        assertEquals(3, receipt.getValue().getVersion());
        assertTrue(receipt.getValue().isDebug());
        assertEquals(7, receipt.getValue().getSourceRevision());
        assertEquals("publisher", receipt.getValue().getCreateBy());
    }

    @Test
    void changedEditRevisionDoesNotPublishPreviouslyValidatedParameters() {
        assertEquals(DefinitionErrorCode.REVISION_CONFLICT, assertThrows(BusinessException.class,
                () -> service.publish("def", 3, 6, "request-1", true, true, "publisher")).getErrorCode());
        assertEquals(2, definition.getPublishedVersion());
        assertEquals(7, definition.getRevision());
        verify(publications, never()).append(any());
    }

    @Test
    void enabledScheduleRejectsIneligiblePublicationWithoutMutatingEitherResource() {
        ScheduleEntity schedule = schedule(true);
        assertEquals(DefinitionErrorCode.SCHEDULE_INCOMPATIBLE, assertThrows(BusinessException.class,
                () -> service.publish("def", 3, 7, "request-1", true, false, "publisher")).getErrorCode());
        assertEquals(2, definition.getPublishedVersion());
        assertFalse(definition.getPublishedDebug());
        assertTrue(schedule.isEnabled());
        verify(definitions, never()).update(eq("ws"), any());
        verify(publications, never()).append(any());
        InOrder locks = inOrder(definitions, schedules);
        locks.verify(definitions).queryByIdForUpdate("ws", "def");
        locks.verify(schedules).queryByIdForUpdate("ws", "schedule");
    }

    @Test
    void disabledScheduleIsNotEnabledWhenPublishingRealtimeParameters() {
        ScheduleEntity schedule = schedule(false);
        service.publish("def", 3, 7, "request-1", true, false, "publisher");
        assertEquals(3, definition.getPublishedVersion());
        assertFalse(schedule.isEnabled());
        assertEquals(4, schedule.getRevision());
    }

    @Test
    void repeatedRequestReturnsCurrentStateWithoutRepublishingOrAppendingHistory() {
        DefinitionPublicationEntity receipt = new DefinitionPublicationEntity();
        receipt.setVersion(2);
        receipt.setSourceRevision(4);
        when(publications.findByRequest("ws", "def", "request-1")).thenReturn(Optional.of(receipt));
        service.publish("def", 2, 4, "request-1", false, true, "publisher");
        assertEquals(7, definition.getRevision());
        assertEquals(3, definition.getCurrentVersion());
        verify(definitions, never()).update(eq("ws"), any());
        verify(publications, never()).append(any());
        assertEquals(DefinitionErrorCode.PUBLICATION_REQUEST_CONFLICT, assertThrows(BusinessException.class,
                () -> service.publish("def", 3, 4, "request-1", false, true, "publisher")).getErrorCode());
    }

    private ScheduleEntity schedule(boolean enabled) {
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setId("schedule");
        schedule.setEnabled(enabled);
        schedule.setRevision(4L);
        when(schedules.queryByTarget("ws", ScheduleTargetType.TASK, "def")).thenReturn(Optional.of(schedule));
        when(schedules.queryByIdForUpdate("ws", "schedule")).thenReturn(Optional.of(schedule));
        return schedule;
    }
}
