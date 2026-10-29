package io.yak.ops.dao.repository.task.impl;

import io.yak.ops.dao.entity.task.TaskGroupQueueEntity;
import io.yak.ops.dao.repository.task.TaskGroupQueueRepository;
import jakarta.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptation of DolphinScheduler TaskGroupDao/TaskGroupQueueDao for immutable
 * workspace-scoped destination identities and Yak Ops string Task IDs.
 *
 * <p>Slot acquisition and release use the original upstream atomic UPDATE
 * contract. The caller's transaction rolls back all previously acquired
 * slots if any resource is busy.
 */
@Repository
public class TaskGroupQueueRepositoryImpl implements TaskGroupQueueRepository {
    private static final String RESOURCE_JOIN =
            "SELECT q.id, q.task_id, g.workspace_id, g.resource_type, g.resource_key"
                    + " FROM yak_ops_task_group_queue q JOIN yak_ops_task_group g ON q.group_id = g.id";

    @Resource
    private JdbcTemplate jdbc;

    @Override
    public void add(TaskGroupQueueEntity queue) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update(
                "INSERT INTO yak_ops_task_group (name, workspace_id, resource_type, resource_key,"
                        + " group_size, use_size, status, create_time, update_time)"
                        + " VALUES (?, ?, ?, ?, 1, 0, 1, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE id = id",
                queue.getResourceType(),
                queue.getWorkspaceId(),
                queue.getResourceType(),
                queue.getResourceKey(),
                now,
                now);
        Long groupId = jdbc.queryForObject(
                "SELECT id FROM yak_ops_task_group WHERE workspace_id = ? AND resource_type = ?"
                        + " AND resource_key = ? FOR UPDATE",
                Long.class,
                queue.getWorkspaceId(),
                queue.getResourceType(),
                queue.getResourceKey());
        if (groupId == null
                || jdbc.update(
                                "UPDATE yak_ops_task_group SET use_size = use_size + 1"
                                        + " WHERE id = ? AND use_size < group_size",
                                groupId)
                        != 1) {
            throw new DuplicateKeyException("TaskGroup slot is occupied");
        }
        jdbc.update(
                "INSERT INTO yak_ops_task_group_queue"
                        + " (id, task_id, group_id, priority, status, force_start, in_queue, create_time, update_time)"
                        + " VALUES (?, ?, ?, 0, 1, 0, 1, ?, ?)",
                queue.getId(),
                queue.getInstanceId(),
                groupId,
                now,
                now);
    }

    @Override
    public Optional<TaskGroupQueueEntity> findResourceForUpdate(String workspaceId, String type, String key) {
        List<TaskGroupQueueEntity> allocated = jdbc.query(
                RESOURCE_JOIN + " WHERE g.workspace_id = ? AND g.resource_type = ?"
                        + " AND g.resource_key = ? FOR UPDATE",
                new SlotMapper(),
                workspaceId,
                type,
                key);
        return allocated.stream().findFirst();
    }

    @Override
    public List<TaskGroupQueueEntity> findByInstance(String workspaceId, String instanceId) {
        return jdbc.query(
                RESOURCE_JOIN + " WHERE g.workspace_id = ? AND q.task_id = ?"
                        + " ORDER BY g.resource_type, g.resource_key",
                new SlotMapper(),
                workspaceId,
                instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releaseOwned(String workspaceId, String instanceId) {
        List<Long> groups = jdbc.queryForList(
                "SELECT q.group_id FROM yak_ops_task_group_queue q"
                        + " JOIN yak_ops_task_group g ON g.id = q.group_id"
                        + " WHERE g.workspace_id = ? AND q.task_id = ? FOR UPDATE",
                Long.class,
                workspaceId,
                instanceId);
        if (groups.isEmpty()) return 0;
        int removed = jdbc.update(
                "DELETE q FROM yak_ops_task_group_queue q"
                        + " JOIN yak_ops_task_group g ON g.id = q.group_id"
                        + " WHERE g.workspace_id = ? AND q.task_id = ?",
                workspaceId,
                instanceId);
        if (removed != groups.size()) {
            throw new IllegalStateException("TaskGroupQueue release was not atomic");
        }
        for (Long id : groups) {
            if (jdbc.update("UPDATE yak_ops_task_group SET use_size = use_size - 1" + " WHERE id = ? AND use_size > 0", id)
                    != 1) {
                throw new IllegalStateException("TaskGroup slot counter is inconsistent");
            }
        }
        return removed;
    }

    private static final class SlotMapper implements RowMapper<TaskGroupQueueEntity> {
        @Override
        public TaskGroupQueueEntity mapRow(ResultSet rows, int rowNum) throws SQLException {
            TaskGroupQueueEntity queue = new TaskGroupQueueEntity();
            queue.setId(rows.getString("id"));
            queue.setInstanceId(rows.getString("task_id"));
            queue.setWorkspaceId(rows.getString("workspace_id"));
            queue.setResourceType(rows.getString("resource_type"));
            queue.setResourceKey(rows.getString("resource_key"));
            return queue;
        }
    }
}
