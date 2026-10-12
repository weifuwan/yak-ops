package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Persisted embedded dispatcher owner identity.
 *
 * <p>The OS process lock remains the authoritative local ownership fence.
 */
@Getter
@Setter
@TableName("yak_ops_task_execution_owner")
public class ExecutionOwnerEntity {
    /** Singleton execution owner record identifier. */
    private String id;
    /** Host eligible to hold the embedded process lock. */
    private String hostId;
    /** Canonical local state directory used for owner verification. */
    private String stateRoot;
    /** Unique JVM incarnation holding dispatch authority. */
    private String ownerIncarnation;
    /** Diagnostic heartbeat, not proof that a process remains alive. */
    private LocalDateTime lastSeenTime;
    /** Timestamp when this owner record was created. */
    private LocalDateTime createTime;
    /** Timestamp of the latest recorded owner update. */
    private LocalDateTime updateTime;
}
