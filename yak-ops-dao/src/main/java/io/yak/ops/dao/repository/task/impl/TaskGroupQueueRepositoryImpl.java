package io.yak.ops.dao.repository.task.impl;

import io.yak.ops.dao.entity.task.TaskGroupQueueEntity;
import io.yak.ops.dao.mapper.task.TaskGroupMapper;
import io.yak.ops.dao.repository.task.TaskGroupQueueRepository;
import io.yak.ops.dao.task.TaskGroupSlotReleasedEvent;
import jakarta.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.dolphinscheduler.common.enums.Flag;
import org.apache.dolphinscheduler.common.enums.TaskGroupQueueStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * DolphinScheduler TaskGroup slot and queue persistence adapted to workspace-scoped
 * resources and string Task Instance IDs. An Instance acquires all its slots or none.
 */
@Repository
public class TaskGroupQueueRepositoryImpl implements TaskGroupQueueRepository {
    private static final int WAITING = TaskGroupQueueStatus.WAIT_QUEUE.getCode();
    private static final int ACQUIRED = TaskGroupQueueStatus.ACQUIRE_SUCCESS.getCode();
    private static final String RESOURCE_JOIN =
            "SELECT q.id, q.task_id, q.priority, q.create_time, g.workspace_id, g.resource_type, g.resource_key"
                    + " FROM yak_ops_task_group_queue q JOIN yak_ops_task_group g ON q.group_id = g.id";

    @Resource
    private JdbcTemplate jdbc;

    @Resource
    private TaskGroupMapper taskGroupMapper;

    @Resource
    private ApplicationEventPublisher events;

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
        if (groupId == null) {
            throw new IllegalStateException("TaskGroup was not created");
        }
        jdbc.update(
                "INSERT INTO yak_ops_task_group_queue"
                        + " (id, task_id, group_id, priority, status, force_start, in_queue, create_time, update_time)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                queue.getId(),
                queue.getInstanceId(),
                groupId,
                queue.getPriority(),
                WAITING,
                Flag.NO.getCode(),
                Flag.YES.getCode(),
                now,
                now);
    }

    /**
     * The caller owns a transaction. Lock the Instance first, then all group rows
     * in deterministic resource order. Check availability and per-group priority before acquiring any slot.
     * This avoids partial multi-resource acquisitions and priority inversion.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryAcquire(String workspaceId, String instanceId) {
        List<String> statuses = jdbc.queryForList(
                "SELECT status FROM yak_ops_task_instance WHERE workspace_id = ? AND id = ? FOR UPDATE",
                String.class,
                workspaceId,
                instanceId);
        if (statuses.size() != 1 || !"SUBMITTED".equals(statuses.get(0))) {
            return false;
        }
        List<QueueSlot> slots = jdbc.query(
                "SELECT q.id, q.task_id, q.group_id, q.status, g.group_size, g.use_size"
                        + " FROM yak_ops_task_group_queue q"
                        + " JOIN yak_ops_task_group g ON g.id = q.group_id"
                        + " WHERE g.workspace_id = ? AND q.task_id = ?"
                        + " ORDER BY g.resource_type, g.resource_key FOR UPDATE",
                (rows, rowNum) -> new QueueSlot(
                        rows.getString("id"),
                        rows.getLong("group_id"),
                        rows.getInt("status"),
                        rows.getInt("group_size"),
                        rows.getInt("use_size")),
                workspaceId,
                instanceId);
        if (slots.isEmpty()) {
            return true;
        }
        if (slots.stream().allMatch(slot -> slot.status == ACQUIRED)) {
            return true;
        }
        if (slots.stream().anyMatch(slot -> slot.status != WAITING)) {
            throw new IllegalStateException("Partial TaskGroup acquisition is not supported");
        }
        for (QueueSlot slot : slots) {
            if (slot.useSize >= slot.groupSize) {
                return false;
            }
            List<String> heads = jdbc.queryForList(
                    "SELECT task_id FROM yak_ops_task_group_queue"
                            + " WHERE group_id = ? AND status = ? AND in_queue = ?"
                            + " ORDER BY priority DESC, create_time ASC, id ASC LIMIT 1",
                    String.class,
                    slot.groupId,
                    WAITING,
                    Flag.YES.getCode());
            if (heads.isEmpty() || !instanceId.equals(heads.get(0))) {
                return false;
            }
        }
        for (QueueSlot slot : slots) {
            if (taskGroupMapper.acquireTaskGroupSlot(slot.groupId) != 1) {
                throw new IllegalStateException("TaskGroup capacity changed while locked");
            }
            if (jdbc.update(
                            "UPDATE yak_ops_task_group_queue SET status = ?, update_time = ?"
                                    + " WHERE id = ? AND status = ?",
                            ACQUIRED,
                            LocalDateTime.now(),
                            slot.id,
                            WAITING)
                    != 1) {
                throw new IllegalStateException("TaskGroup queue status changed while locked");
            }
        }
        return true;
    }

    @Override
    public boolean allAcquired(String workspaceId, String instanceId) {
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_task_group_queue q"
                        + " JOIN yak_ops_task_group g ON g.id = q.group_id"
                        + " WHERE g.workspace_id = ? AND q.task_id = ? AND q.status <> ?",
                Integer.class,
                workspaceId,
                instanceId,
                ACQUIRED);
        return pending != null && pending == 0;
    }

    @Override
    public List<TaskGroupQueueEntity> waiting(int limit, TaskGroupQueueEntity after) {
        String sql = RESOURCE_JOIN + " JOIN yak_ops_task_instance t ON t.id = q.task_id"
                + " WHERE q.status = ? AND q.in_queue = ? AND t.status = 'SUBMITTED'";
        if (after == null) {
            return jdbc.query(
                    sql + " ORDER BY q.priority DESC, q.create_time ASC, q.id ASC LIMIT ?",
                    new SlotMapper(),
                    WAITING,
                    Flag.YES.getCode(),
                    Math.max(1, Math.min(limit, 1000)));
        }
        // Keyset pagination must match all three ORDER BY fields. OFFSET pagination
        // would skip entries when another task is acquired or canceled mid-scan.
        return jdbc.query(
                sql + " AND (q.priority < ? OR (q.priority = ?"
                        + " AND (q.create_time > ? OR (q.create_time = ? AND q.id > ?))))"
                        + " ORDER BY q.priority DESC, q.create_time ASC, q.id ASC LIMIT ?",
                new SlotMapper(),
                WAITING,
                Flag.YES.getCode(),
                after.getPriority(),
                after.getPriority(),
                after.getCreateTime(),
                after.getCreateTime(),
                after.getId(),
                Math.max(1, Math.min(limit, 1000)));
    }

    @Override
    public Optional<TaskGroupQueueEntity> findResourceForUpdate(String workspaceId, String type, String key) {
        List<TaskGroupQueueEntity> allocated = jdbc.query(
                RESOURCE_JOIN + " WHERE g.workspace_id = ? AND g.resource_type = ?"
                        + " AND g.resource_key = ? AND q.status = ? FOR UPDATE",
                new SlotMapper(),
                workspaceId,
                type,
                key,
                ACQUIRED);
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
        List<QueueSlot> slots = jdbc.query(
                "SELECT q.id, q.task_id, q.group_id, q.status, g.group_size, g.use_size"
                        + " FROM yak_ops_task_group_queue q"
                        + " JOIN yak_ops_task_group g ON g.id = q.group_id"
                        + " WHERE g.workspace_id = ? AND q.task_id = ?"
                        + " ORDER BY g.resource_type, g.resource_key FOR UPDATE",
                (rows, rowNum) -> new QueueSlot(
                        rows.getString("id"),
                        rows.getLong("group_id"),
                        rows.getInt("status"),
                        rows.getInt("group_size"),
                        rows.getInt("use_size")),
                workspaceId,
                instanceId);
        if (slots.isEmpty()) {
            return 0;
        }
        int removed = jdbc.update(
                "DELETE q FROM yak_ops_task_group_queue q"
                        + " JOIN yak_ops_task_group g ON g.id = q.group_id"
                        + " WHERE g.workspace_id = ? AND q.task_id = ?",
                workspaceId,
                instanceId);
        if (removed != slots.size()) {
            throw new IllegalStateException("TaskGroupQueue release was not atomic");
        }
        for (QueueSlot slot : slots) {
            if (slot.status == ACQUIRED && taskGroupMapper.releaseTaskGroupSlot(slot.groupId) != 1) {
                throw new IllegalStateException("TaskGroup slot counter is inconsistent");
            }
        }
        events.publishEvent(new TaskGroupSlotReleasedEvent(instanceId));
        return removed;
    }

    /**
     * Correct counters after restart using persisted acquired rows as evidence.
     * In-flight and RECONCILING jobs retain their rows and therefore their slots.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcile() {
        // The upstream coordinator removes orphaned and terminal queue entries
        // before repairing slot counters. RECONCILING must remain reserved.
        List<QueueOwner> stale = jdbc.query(
                "SELECT DISTINCT q.task_id, g.workspace_id"
                        + " FROM yak_ops_task_group_queue q"
                        + " JOIN yak_ops_task_group g ON g.id = q.group_id"
                        + " LEFT JOIN yak_ops_task_instance t ON t.id = q.task_id"
                        + " WHERE t.id IS NULL OR t.status IN ('FINISHED', 'FAILED', 'CANCELED')",
                (rows, rowNum) -> new QueueOwner(rows.getString("workspace_id"), rows.getString("task_id")));
        for (QueueOwner owner : stale) {
            releaseOwned(owner.workspaceId, owner.instanceId);
        }
        List<Long> groupIds = jdbc.queryForList("SELECT id FROM yak_ops_task_group ORDER BY id FOR UPDATE", Long.class);
        for (Long groupId : groupIds) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM yak_ops_task_group_queue"
                            + " WHERE group_id = ? AND status = ? AND in_queue = ? AND force_start = ?",
                    Integer.class,
                    groupId,
                    ACQUIRED,
                    Flag.YES.getCode(),
                    Flag.NO.getCode());
            jdbc.update(
                    "UPDATE yak_ops_task_group SET use_size = ?, update_time = ?" + " WHERE id = ? AND use_size <> ?",
                    count,
                    LocalDateTime.now(),
                    groupId,
                    count);
        }
    }

    private static final class SlotMapper implements RowMapper<TaskGroupQueueEntity> {
        @Override
        public TaskGroupQueueEntity mapRow(ResultSet rows, int rowNum) throws SQLException {
            TaskGroupQueueEntity queue = new TaskGroupQueueEntity();
            queue.setId(rows.getString("id"));
            queue.setInstanceId(rows.getString("task_id"));
            queue.setPriority(rows.getInt("priority"));
            queue.setCreateTime(rows.getTimestamp("create_time").toLocalDateTime());
            queue.setWorkspaceId(rows.getString("workspace_id"));
            queue.setResourceType(rows.getString("resource_type"));
            queue.setResourceKey(rows.getString("resource_key"));
            return queue;
        }
    }

    private static final class QueueOwner {
        private final String workspaceId;
        private final String instanceId;

        private QueueOwner(String workspaceId, String instanceId) {
            this.workspaceId = workspaceId;
            this.instanceId = instanceId;
        }
    }

    private static final class QueueSlot {
        private final String id;
        private final long groupId;
        private final int status;
        private final int groupSize;
        private final int useSize;

        private QueueSlot(String id, long groupId, int status, int groupSize, int useSize) {
            this.id = id;
            this.groupId = groupId;
            this.status = status;
            this.groupSize = groupSize;
            this.useSize = useSize;
        }
    }
}
