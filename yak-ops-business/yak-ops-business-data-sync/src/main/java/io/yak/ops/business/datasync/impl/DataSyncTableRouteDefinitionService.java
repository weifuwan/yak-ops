package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.dto.datasync.DataSyncTableRouteDTO;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncTableRouteEntity;
import io.yak.ops.dao.repository.datasync.DataSyncTableRouteRepository;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * OFFLINE Task 的有序 Route 定义更新：同一业务事务内保持 Route ID 并安全处理 sortOrder 唯一索引。
 *
 * <p>本类不持有 Runtime 或 Schema 校验。调用方先按 Datasource Catalog 解析、验证并冻结请求中的路径与 Mapping。</p>
 *
 * @author weifuwan
 * @since 2026-10-08
 */
@Component
public class DataSyncTableRouteDefinitionService {

    private static final int SORT_ORDER_STAGING_OFFSET = 1024;

    @Resource
    private DataSyncTableRouteRepository tableRouteRepository;

    /** 创建 / 编辑前检查请求中的 Route ID 只能引用同一 Task 下已存在的 Route。 */
    public void requireOwnedIds(String workspaceId, String taskId, List<DataSyncTableRouteDTO> requested) {
        Map<String, DataSyncTableRouteEntity> owned = new HashMap<>();
        if (taskId != null) {
            for (DataSyncTableRouteEntity route : tableRouteRepository.queryByTask(workspaceId, taskId)) {
                owned.put(route.getId(), route);
            }
        }
        Set<String> seen = new HashSet<>();
        for (DataSyncTableRouteDTO definition : requested) {
            String id = StringUtils.trimToNull(definition.getId());
            if (id == null) continue;
            if (!seen.add(id) || taskId == null || !owned.containsKey(id)) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "Route ID 不属于当前任务或重复");
            }
        }
    }

    /** 仅字段或 Route 集合/顺序实际变化时推进 Task definitionVersion。 */
    public boolean changed(String workspaceId, String taskId, List<DataSyncTableRouteDTO> requested) {
        List<DataSyncTableRouteEntity> existing = tableRouteRepository.queryByTask(workspaceId, taskId);
        return !same(existing, requested);
    }

    /**
     * 调用者必须处于 @Transactional 业务事务；任何持久化失败触发 Root Task 更新回滚。
     * 先把原 sortOrder 临时移动到 1024+ 区间，防止交换两条 Route 的顺序时碰撞唯一索引。
     */
    public void reconcile(
            String workspaceId, String taskId, List<DataSyncTableRouteDTO> requested, String operatorUserId) {
        List<DataSyncTableRouteEntity> existing = tableRouteRepository.queryByTask(workspaceId, taskId);
        if (same(existing, requested)) return;

        Map<String, DataSyncTableRouteEntity> byId = new HashMap<>();
        for (DataSyncTableRouteEntity route : existing) byId.put(route.getId(), route);
        Set<String> retain = new HashSet<>();
        for (DataSyncTableRouteDTO dto : requested) {
            String id = StringUtils.trimToNull(dto.getId());
            if (id != null) {
                if (!retain.add(id) || !byId.containsKey(id)) {
                    throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "Route ID 不属于当前任务或重复");
                }
            }
        }

        // 变更时先移动已有唯一顺序，以便重新排列、添加和删除共用一次事务。
        for (int index = 0; index < existing.size(); index++) {
            DataSyncTableRouteEntity route = existing.get(index);
            route.setSortOrder(SORT_ORDER_STAGING_OFFSET + index);
            route.initUpdate(operatorUserId);
            requireUpdated(workspaceId, route);
        }
        for (DataSyncTableRouteEntity route : existing) {
            if (!retain.contains(route.getId()) && tableRouteRepository.deleteById(workspaceId, route.getId()) <= 0) {
                throw new DataSyncException(DataSyncErrorCode.UPDATE_TASK_FAILED, "移除 Table Route 失败");
            }
        }
        for (int index = 0; index < requested.size(); index++) {
            DataSyncTableRouteDTO dto = requested.get(index);
            String id = StringUtils.trimToNull(dto.getId());
            DataSyncTableRouteEntity route = id == null ? new DataSyncTableRouteEntity() : byId.get(id);
            apply(route, workspaceId, taskId, dto, index);
            if (id == null) {
                route.initCreate(operatorUserId);
                if (tableRouteRepository.add(route) == null) {
                    throw new DataSyncException(DataSyncErrorCode.CREATE_TASK_FAILED, "保存 Table Route 失败");
                }
            } else {
                route.initUpdate(operatorUserId);
                requireUpdated(workspaceId, route);
            }
        }
    }

    private void requireUpdated(String workspaceId, DataSyncTableRouteEntity route) {
        if (tableRouteRepository.update(workspaceId, route) == null) {
            throw new DataSyncException(DataSyncErrorCode.UPDATE_TASK_FAILED, "更新 Table Route 失败");
        }
    }

    private void apply(
            DataSyncTableRouteEntity target,
            String workspaceId,
            String taskId,
            DataSyncTableRouteDTO source,
            int sortOrder) {
        target.setWorkspaceId(workspaceId);
        target.setTaskId(taskId);
        target.setSourceDatabase(source.getSourceDatabase());
        target.setSourceSchema(source.getSourceSchema());
        target.setSourceTable(source.getSourceTable());
        target.setTargetDatabase(source.getTargetDatabase());
        target.setTargetSchema(source.getTargetSchema());
        target.setTargetTable(source.getTargetTable());
        target.setAutoCreateTable(false);
        target.setMappingConfig(null);
        target.setSortOrder(sortOrder);
    }

    private boolean same(List<DataSyncTableRouteEntity> existing, List<DataSyncTableRouteDTO> requested) {
        if (existing.size() != requested.size()) return false;
        for (int index = 0; index < existing.size(); index++) {
            DataSyncTableRouteEntity route = existing.get(index);
            DataSyncTableRouteDTO dto = requested.get(index);
            if (!Objects.equals(route.getId(), StringUtils.trimToNull(dto.getId()))
                    || !Objects.equals(route.getSortOrder(), index)
                    || !Objects.equals(route.getSourceDatabase(), dto.getSourceDatabase())
                    || !Objects.equals(route.getSourceSchema(), dto.getSourceSchema())
                    || !Objects.equals(route.getSourceTable(), dto.getSourceTable())
                    || !Objects.equals(route.getTargetDatabase(), dto.getTargetDatabase())
                    || !Objects.equals(route.getTargetSchema(), dto.getTargetSchema())
                    || !Objects.equals(route.getTargetTable(), dto.getTargetTable())
                    || Boolean.TRUE.equals(route.getAutoCreateTable())
                    || StringUtils.isNotBlank(route.getMappingConfig())) {
                return false;
            }
        }
        return true;
    }

}
