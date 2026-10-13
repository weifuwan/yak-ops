package io.yak.ops.dao.repository.task.impl;

import io.yak.ops.dao.repository.task.ExecutionOwnerRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Serializes embedded owner registration through the durable singleton owner row. */
@Repository
public class ExecutionOwnerRepositoryImpl implements ExecutionOwnerRepository {

    private static final String OWNER_ID = "embedded";

    @Resource
    private JdbcTemplate jdbc;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(String hostId, String stateRoot, String incarnation) {
        List<Map<String, Object>> owners = jdbc.queryForList(
                "SELECT host_id, state_root FROM yak_ops_task_execution_owner WHERE id = ? FOR UPDATE", OWNER_ID);
        LocalDateTime now = LocalDateTime.now();
        if (owners.isEmpty()) {
            jdbc.update(
                    "INSERT INTO yak_ops_task_execution_owner"
                            + " (id, host_id, state_root, owner_incarnation, last_seen_time, create_time, update_time)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                    OWNER_ID,
                    hostId,
                    stateRoot,
                    incarnation,
                    now,
                    now,
                    now);
            return;
        }

        Map<String, Object> previous = owners.getFirst();
        if (!hostId.equals(previous.get("host_id")) || !stateRoot.equals(previous.get("state_root"))) {
            throw new IllegalStateException("Embedded execution owner belongs to another host or state root");
        }

        jdbc.update(
                "UPDATE yak_ops_task_execution_owner SET owner_incarnation = ?, last_seen_time = ?,"
                        + " update_time = ? WHERE id = ?",
                incarnation,
                now,
                now,
                OWNER_ID);
    }
}
