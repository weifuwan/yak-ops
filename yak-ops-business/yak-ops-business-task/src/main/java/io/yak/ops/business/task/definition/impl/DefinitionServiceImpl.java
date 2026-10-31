package io.yak.ops.business.task.definition.impl;

import io.yak.ops.business.task.definition.DefinitionService;
import io.yak.ops.business.task.definition.PublicationService;
import io.yak.ops.common.bean.dto.task.CreateDefinitionDTO;
import io.yak.ops.common.bean.dto.task.DefinitionQueryDTO;
import io.yak.ops.common.bean.dto.task.UpdateDefinitionDTO;
import io.yak.ops.common.bean.vo.task.DefinitionVO;
import io.yak.ops.common.bean.vo.task.DefinitionVersionVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.repository.schedule.ScheduleRepository;
import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Task Definition is the sole owner of identity, immutable versions and publication. */
@Service
public class DefinitionServiceImpl implements DefinitionService {
    @Resource
    private DefinitionRepository definitionRepository;

    @Resource
    private DefinitionVersionRepository versionRepository;

    @Resource
    private TaskPluginManager pluginRegistry;

    @Resource
    private TaskValidationContext validationContext;

    @Resource
    private PublicationService publicationService;

    @Resource
    private ScheduleRepository schedules;

    @Resource
    private InstanceRepository instances;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionVO create(CreateDefinitionDTO dto, String operatorId) {
        if (dto == null) throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        String name = requiredName(dto.getName());
        checkNameAvailable(workspaceId, name, null);
        String type = dto.getTaskType();
        String parameters = validatedParameters(type, dto.getParameters());
        DefinitionEntity definition = new DefinitionEntity();
        definition.setWorkspaceId(workspaceId);
        definition.setName(name);
        definition.setTaskType(type);
        definition.setStatus(DefinitionStatus.UNPUBLISHED);
        definition.setCurrentVersion(1);
        definition.setDebug(Boolean.TRUE.equals(dto.getDebug()));
        definition.setRemark(dto.getRemark());
        definition.initCreate(operatorId);
        definitionRepository.add(definition);
        appendVersion(definition, parameters, operatorId);
        return toVO(definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionVO update(String id, UpdateDefinitionDTO dto, String operatorId) {
        if (dto == null) throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        DefinitionEntity definition = requireDefinition(id, true);
        requireUnpublished(definition);
        requireNoActiveInstance(definition);
        String name = requiredName(dto.getName());
        checkNameAvailable(definition.getWorkspaceId(), name, id);
        if (StringUtils.isNotBlank(dto.getParameters())) {
            String updated = validatedParameters(definition.getTaskType(), dto.getParameters());
            DefinitionVersionEntity stored = currentVersion(definition);
            TaskPlugin plugin = pluginRegistry.get(definition.getTaskType());
            plugin.validateUpdate(
                    plugin.parse(stored.getParameterSchemaVersion(), stored.getParameters()),
                    plugin.parse(updated),
                    validationContext);
            String existing = stored.getParameters();
            if (!updated.equals(existing)) {
                definition.setCurrentVersion(definition.getCurrentVersion() + 1);
                appendVersion(definition, updated, operatorId);
            }
        }
        definition.setName(name);
        definition.setRemark(dto.getRemark());
        if (dto.getDebug() != null) {
            definition.setDebug(dto.getDebug());
        }
        definition.initUpdate(operatorId);
        if (definitionRepository.update(definition.getWorkspaceId(), definition) == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
        return toVO(definition);
    }

    @Override
    public DefinitionVO publish(String id, String operatorId) {
        DefinitionEntity definition = requireDefinition(id, false);
        int expectedVersion = definition.getCurrentVersion();
        validatePublication(definition.getTaskType(), currentVersion(definition));
        return toVO(publicationService.publish(id, expectedVersion, operatorId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionVO unpublish(String id, String operatorId) {
        DefinitionEntity definition = requireDefinition(id, true);
        requireNoActiveInstance(definition);
        definition.setStatus(DefinitionStatus.UNPUBLISHED);
        definition.initUpdate(operatorId);
        schedules.disableForTarget(
                definition.getWorkspaceId(), ScheduleTargetType.TASK, id, definition.getUpdateTime(), operatorId);
        definitionRepository.update(definition.getWorkspaceId(), definition);
        return toVO(definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, String operatorId) {
        DefinitionEntity definition = requireDefinition(id, true);
        requireUnpublished(definition);
        requireNoActiveInstance(definition);
        String operator = operatorId;
        if (StringUtils.isBlank(operator)) {
            operator = "system";
        }
        schedules.disableForTarget(definition.getWorkspaceId(), ScheduleTargetType.TASK, id, DateUtils.now(), operator);
        if (!definitionRepository.softDelete(definition.getWorkspaceId(), id, DateUtils.now(), operator)) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
    }

    @Override
    public DefinitionVO query(String id) {
        return toVO(requireDefinition(id, false));
    }

    @Override
    public PagingData<DefinitionVO> queryPage(DefinitionQueryDTO dto) {
        if (dto == null
                || dto.getPageNo() == null
                || dto.getPageSize() == null
                || dto.getPageNo() < 1
                || dto.getPageSize() < 1
                || dto.getPageSize() > 200
                || !dto.getSorts().isEmpty()) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        Map<String, String> filters = dto.getParameterFilters();
        if (filters == null) {
            filters = Map.of();
        }
        if (filters.size() > 16
                || filters.keySet().stream()
                        .anyMatch(k -> k == null
                                || !java.util.Set.of(
                                                "sourceType",
                                                "sinkType",
                                                "runtimeMode",
                                                "tableScope",
                                                "sourceDataSourceId",
                                                "targetDataSourceId",
                                                "dataSyncProfile")
                                        .contains(k))) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        if (filters.containsKey("dataSyncProfile")) {
            String profile = filters.get("dataSyncProfile");
            if (!"DATA_SYNC".equals(StringUtils.trimToNull(dto.getTaskType()))
                    || profile == null
                    || !java.util.Set.of("SINGLE_TABLE_BATCH", "DATABASE_REPLICATION")
                            .contains(profile)) {
                throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
            }
        }
        Integer status = null;
        if (dto.getStatus() != null) {
            status = dto.getStatus().getValue();
        }
        DefinitionPageQuery filter = new DefinitionPageQuery(
                WorkspaceContext.requireWorkspaceId(),
                dto.getPageNo(),
                dto.getPageSize(),
                StringUtils.trimToNull(dto.getKeyword()),
                StringUtils.trimToNull(dto.getTaskType()),
                status,
                filters);
        return PagingData.from(definitionRepository.queryPage(filter).map(this::toVO));
    }

    @Override
    public List<DefinitionVersionVO> queryVersions(String id) {
        DefinitionEntity definition = requireDefinition(id, false);
        return versionRepository.queryByDefinition(definition.getWorkspaceId(), definition.getId()).stream()
                .map(v -> {
                    DefinitionVersionVO vo = BeanCopyUtils.copy(v, DefinitionVersionVO.class);
                    return vo;
                })
                .toList();
    }

    private DefinitionVersionEntity currentVersion(DefinitionEntity entity) {
        return versionRepository
                .queryCurrent(entity.getWorkspaceId(), entity.getId(), entity.getCurrentVersion())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private void requireUnpublished(DefinitionEntity entity) {
        if (entity.getStatus() != DefinitionStatus.UNPUBLISHED) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
    }

    /** Called while the Definition row is locked, just like execution admission. */
    private void requireNoActiveInstance(DefinitionEntity definition) {
        if (!instances
                .latest(definition.getWorkspaceId(), List.of(definition.getId()), true)
                .isEmpty()) {
            throw new BusinessException(CommonErrorCode.TASK_INSTANCE_ACTIVE);
        }
    }

    private DefinitionEntity requireDefinition(String id, boolean forUpdate) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        java.util.Optional<DefinitionEntity> definition;
        if (forUpdate) {
            definition = definitionRepository.queryByIdForUpdate(workspaceId, id);
        } else {
            definition = definitionRepository.queryById(workspaceId, id);
        }
        return definition.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private DefinitionVO toVO(DefinitionEntity entity) {
        DefinitionVersionEntity version = versionRepository
                .queryCurrent(entity.getWorkspaceId(), entity.getId(), entity.getCurrentVersion())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        DefinitionVO vo = BeanCopyUtils.copy(entity, DefinitionVO.class);
        vo.setStatus(entity.getStatus().name());
        vo.setDefinitionVersion(entity.getCurrentVersion());
        vo.setParameters(version.getParameters());
        vo.setParameterSchemaVersion(version.getParameterSchemaVersion());
        return vo;
    }

    private String validatedParameters(String type, String parameters) {
        TaskPlugin plugin = pluginRegistry.get(type);
        ReadonlyConfig parsed = plugin.parse(parameters);
        plugin.validateReferences(parsed, validationContext);
        return ConfigParser.toJson(parsed);
    }

    private void validatePublication(String type, DefinitionVersionEntity version) {
        TaskPlugin plugin = pluginRegistry.get(type);
        ReadonlyConfig parsed = plugin.parse(version.getParameterSchemaVersion(), version.getParameters());
        plugin.validateReferences(parsed, validationContext);
        plugin.validate(parsed, validationContext);
    }

    private void appendVersion(DefinitionEntity entity, String json, String operator) {
        DefinitionVersionEntity version = new DefinitionVersionEntity();
        version.setWorkspaceId(entity.getWorkspaceId());
        version.setDefinitionId(entity.getId());
        version.setVersion(entity.getCurrentVersion());
        version.setParameterSchemaVersion(
                pluginRegistry.get(entity.getTaskType()).parameterSchemaVersion());
        version.setParameters(json);
        version.initCreate(operator);
        versionRepository.append(version);
    }

    private String requiredName(String name) {
        String trimmedName = StringUtils.trimToNull(name);
        if (trimmedName == null || trimmedName.length() > 128) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        return trimmedName;
    }

    private void checkNameAvailable(String workspaceId, String name, String excludeId) {
        if (definitionRepository.existsByName(workspaceId, name, excludeId)) {
            throw new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION);
        }
    }
}
