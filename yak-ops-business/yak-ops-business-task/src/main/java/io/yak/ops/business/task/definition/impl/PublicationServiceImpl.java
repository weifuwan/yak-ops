package io.yak.ops.business.task.definition.impl;

import io.yak.ops.business.task.definition.PublicationService;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pins the externally validated version and updates publication state atomically. */
@Service
public class PublicationServiceImpl implements PublicationService {

    private final DefinitionRepository definitions;
    private final DefinitionPublicationRepository publications;
    private final ScheduleRepository schedules;

    public PublicationServiceImpl(
            DefinitionRepository definitions,
            DefinitionPublicationRepository publications,
            ScheduleRepository schedules) {
        this.definitions = definitions;
        this.publications = publications;
        this.schedules = schedules;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionEntity publish(
            String id,
            int version,
            long expectedRevision,
            String requestId,
            boolean debug,
            boolean schedulable,
            String operatorId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DefinitionEntity definition = definitions
                .queryByIdForUpdate(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        Optional<DefinitionPublicationEntity> replay = publications.findByRequest(workspaceId, id, requestId);
        if (replay.isPresent()) {
            requireSameRequest(replay.orElseThrow(), version, expectedRevision);
            return definition;
        }
        if (definition.getRevision() != expectedRevision || definition.isDebug() != debug) {
            throw new BusinessException(DefinitionErrorCode.REVISION_CONFLICT);
        }
        if (!schedulable) {
            Optional<ScheduleEntity> selected = schedules.queryByTarget(workspaceId, ScheduleTargetType.TASK, id);
            if (selected.isPresent()) {
                ScheduleEntity schedule = schedules
                        .queryByIdForUpdate(workspaceId, selected.orElseThrow().getId())
                        .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
                if (schedule.isEnabled()) {
                    throw new BusinessException(DefinitionErrorCode.SCHEDULE_INCOMPATIBLE);
                }
            }
        }
        definition.setStatus(DefinitionStatus.PUBLISHED);
        definition.setPublishedVersion(version);
        definition.setPublishedDebug(debug);
        definition.setRevision(Math.addExact(definition.getRevision(), 1));
        definition.initUpdate(operatorId);
        if (definitions.update(workspaceId, definition) == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
        DefinitionPublicationEntity publication = new DefinitionPublicationEntity();
        publication.setWorkspaceId(workspaceId);
        publication.setDefinitionId(id);
        publication.setVersion(version);
        publication.setDebug(debug);
        publication.setSourceRevision(expectedRevision);
        publication.setRequestId(requestId);
        publication.initCreate(operatorId);
        publications.append(publication);
        return definition;
    }

    static void requireSameRequest(DefinitionPublicationEntity publication, int version, long expectedRevision) {
        if (publication.getVersion() != version || publication.getSourceRevision() != expectedRevision) {
            throw new BusinessException(DefinitionErrorCode.PUBLICATION_REQUEST_CONFLICT);
        }
    }
}
