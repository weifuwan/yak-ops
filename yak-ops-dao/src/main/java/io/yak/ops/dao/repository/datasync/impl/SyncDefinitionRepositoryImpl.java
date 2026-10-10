package io.yak.ops.dao.repository.datasync.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.SyncDefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.mapper.datasync.SyncDefinitionMapper;
import io.yak.ops.dao.repository.datasync.SyncDefinitionPageQuery;
import io.yak.ops.dao.repository.datasync.SyncDefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * DATA_SYNC 单表配置在通用 Task Definition 下的持久化投影。
 *
 * <p>通用名称、发布状态和版本仅写入 Definition；插件专属字段仅写入 Data Sync 配置表。
 * 两次写入必须由调用方的 Business 事务统一提交，不允许留下半条任务。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Repository
public class SyncDefinitionRepositoryImpl implements SyncDefinitionRepository {

    private static final String DATA_SYNC = "DATA_SYNC";

    @Resource
    private SyncDefinitionMapper taskMapper;

    @Resource
    private DefinitionRepository definitionRepository;

    @Resource
    private DefinitionVersionRepository versionRepository;

    @Override
    public SyncDefinitionEntity add(SyncDefinitionEntity entity) {
        if (entity == null || entity.getId() == null || entity.getWorkspaceId() == null) {
            throw new IllegalArgumentException("DATA_SYNC definition requires initialized ID and workspace");
        }
        DefinitionEntity definition = toDefinition(entity);
        definitionRepository.add(definition);
        taskMapper.insert(entity);
        versionRepository.add(toVersion(entity));
        return entity;
    }

    @Override
    public PageData<SyncDefinitionEntity> queryPage(String workspaceId, SyncDefinitionPageQuery query) {
        SyncDefinitionPageQuery condition =
                query == null ? new SyncDefinitionPageQuery(1, 10, null, null, null, null, null) : query;
        Page<String> page = Page.of(Math.max(1, condition.pageNo()), Math.max(1, condition.pageSize()));
        IPage<String> matched = taskMapper.selectDefinitionPageIds(
                page,
                workspaceId,
                condition.keyword(),
                condition.syncType() == null ? null : condition.syncType().getValue(),
                condition.status() == null ? null : condition.status().getValue(),
                condition.sourceDataSourceId(),
                condition.targetDataSourceId());
        return new PageData<>(
                queryByIds(workspaceId, matched.getRecords()),
                matched.getTotal(),
                matched.getPages(),
                matched.getCurrent(),
                matched.getSize());
    }

    @Override
    public Optional<SyncDefinitionEntity> queryById(String workspaceId, String id) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(id)) return Optional.empty();
        Optional<DefinitionEntity> definition = definitionRepository.queryById(workspaceId, id);
        if (definition.isEmpty() || !DATA_SYNC.equals(definition.get().getTaskType())) {
            return Optional.empty();
        }
        SyncDefinitionEntity detail = taskMapper.selectOne(Wrappers.<SyncDefinitionEntity>lambdaQuery()
                .eq(SyncDefinitionEntity::getWorkspaceId, workspaceId)
                .eq(SyncDefinitionEntity::getId, id));
        return Optional.ofNullable(detail).map(value -> join(value, definition.get()));
    }

    @Override
    public List<SyncDefinitionEntity> queryRealtimeDesiredRunning() {
        List<String> ids = taskMapper.selectRealtimeDesiredRunningIds();
        if (ids.isEmpty()) return List.of();
        Map<String, DefinitionEntity> definitions = index(definitionRepository.queryByIdsForRecovery(ids));
        return taskMapper.selectBatchIds(ids).stream()
                .map(sync -> join(sync, requireDefinition(definitions, sync.getId())))
                .toList();
    }

    @Override
    public SyncDefinitionEntity update(String workspaceId, SyncDefinitionEntity entity) {
        if (StringUtils.isBlank(workspaceId) || entity == null || StringUtils.isBlank(entity.getId())) return null;
        DefinitionEntity existing =
                definitionRepository.queryById(workspaceId, entity.getId()).orElse(null);
        if (existing == null || !DATA_SYNC.equals(existing.getTaskType())) return null;
        if (entity.getDefinitionVersion() == null || entity.getDefinitionVersion() < existing.getDefinitionVersion()) {
            throw new IllegalArgumentException("Stale DATA_SYNC definition version");
        }

        DefinitionEntity definition = toDefinition(entity);
        if (definitionRepository.update(workspaceId, definition) == null) return null;
        int updated = taskMapper.update(
                entity,
                Wrappers.<SyncDefinitionEntity>lambdaUpdate()
                        .eq(SyncDefinitionEntity::getWorkspaceId, workspaceId)
                        .eq(SyncDefinitionEntity::getId, entity.getId()));
        if (updated <= 0) {
            throw new IllegalStateException("Missing DATA_SYNC parameters for definition " + entity.getId());
        }
        if (!Objects.equals(existing.getDefinitionVersion(), entity.getDefinitionVersion())) {
            versionRepository.add(toVersion(entity));
        }
        return entity;
    }

    @Override
    public int deleteById(String workspaceId, String id) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(id)) return 0;
        int removed = taskMapper.delete(Wrappers.<SyncDefinitionEntity>lambdaQuery()
                .eq(SyncDefinitionEntity::getWorkspaceId, workspaceId)
                .eq(SyncDefinitionEntity::getId, id));
        if (removed <= 0) return 0;
        if (definitionRepository.deleteById(workspaceId, id) != 1) {
            throw new IllegalStateException("Missing Task Definition for DATA_SYNC " + id);
        }
        // Immutable versions stay available for historical execution references.
        return removed;
    }

    @Override
    public boolean existsByName(String workspaceId, String name, String excludeId) {
        return definitionRepository.existsByName(workspaceId, name, excludeId);
    }

    private List<SyncDefinitionEntity> queryByIds(String workspaceId, List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Map<String, DefinitionEntity> definitions = index(definitionRepository.queryByIds(workspaceId, ids));
        Map<String, SyncDefinitionEntity> configurations = new HashMap<>();
        for (SyncDefinitionEntity entity : taskMapper.selectBatchIds(ids)) {
            configurations.put(entity.getId(), entity);
        }
        return ids.stream()
                .map(id -> {
                    SyncDefinitionEntity config = configurations.get(id);
                    if (config == null) throw new IllegalStateException("Missing DATA_SYNC parameters for " + id);
                    return join(config, requireDefinition(definitions, id));
                })
                .toList();
    }

    private Map<String, DefinitionEntity> index(List<DefinitionEntity> records) {
        Map<String, DefinitionEntity> definitions = new HashMap<>();
        for (DefinitionEntity definition : records) {
            definitions.put(definition.getId(), definition);
        }
        return definitions;
    }

    private DefinitionEntity requireDefinition(Map<String, DefinitionEntity> definitions, String id) {
        DefinitionEntity definition = definitions.get(id);
        if (definition == null || !DATA_SYNC.equals(definition.getTaskType())) {
            throw new IllegalStateException("Missing Task Definition for DATA_SYNC " + id);
        }
        return definition;
    }

    private SyncDefinitionEntity join(SyncDefinitionEntity config, DefinitionEntity definition) {
        config.setName(definition.getName());
        config.setStatus(DataSyncTaskStatus.valueOf(definition.getStatus().name()));
        config.setDefinitionVersion(definition.getDefinitionVersion());
        config.setRemark(definition.getRemark());
        config.setCreateTime(definition.getCreateTime());
        config.setUpdateTime(definition.getUpdateTime());
        config.setCreateBy(definition.getCreateBy());
        config.setUpdateBy(definition.getUpdateBy());
        return config;
    }

    private DefinitionEntity toDefinition(SyncDefinitionEntity sync) {
        DefinitionEntity definition = BeanCopyUtils.copy(sync, DefinitionEntity.class, "status");
        definition.setTaskType(DATA_SYNC);
        definition.setStatus(DefinitionStatus.valueOf(sync.getStatus().name()));
        return definition;
    }

    private DefinitionVersionEntity toVersion(SyncDefinitionEntity sync) {
        DefinitionVersionEntity version = new DefinitionVersionEntity();
        version.setWorkspaceId(sync.getWorkspaceId());
        version.setDefinitionId(sync.getId());
        version.setTaskType(DATA_SYNC);
        version.setName(sync.getName());
        version.setVersion(sync.getDefinitionVersion());
        version.setParametersSnapshot(parametersSnapshot(sync));
        version.initCreate(sync.getUpdateBy());
        return version;
    }

    private String parametersSnapshot(SyncDefinitionEntity sync) {
        ObjectNode params = JSONUtils.createObjectNode();
        params.put("syncType", sync.getSyncType().name());
        params.put("writeMode", sync.getWriteMode().name());
        params.put("sourceDataSourceId", sync.getSourceDataSourceId());
        params.put("sourceDatabase", sync.getSourceDatabase());
        params.put("sourceSchema", sync.getSourceSchema());
        params.put("sourceTable", sync.getSourceTable());
        params.put("targetDataSourceId", sync.getTargetDataSourceId());
        params.put("targetDatabase", sync.getTargetDatabase());
        params.put("targetSchema", sync.getTargetSchema());
        params.put("targetTable", sync.getTargetTable());
        if (StringUtils.isNotBlank(sync.getRuntimeConfig())) {
            String key = sync.getSyncType() == DataSyncType.REALTIME ? "realtimeConfig" : "runtimeConfig";
            params.set(key, JSONUtils.readTree(sync.getRuntimeConfig()));
        }
        if (StringUtils.isNotBlank(sync.getRetryPolicy())) {
            params.set("retryPolicy", JSONUtils.readTree(sync.getRetryPolicy()));
        }
        if (Boolean.TRUE.equals(sync.getAutoCreateTable())) {
            params.put("legacyAutoCreateTable", true);
        }
        if (StringUtils.isNotBlank(sync.getMappingConfig())) {
            params.put("legacyMappingConfig", sync.getMappingConfig());
        }
        return JSONUtils.toJson(params);
    }
}
