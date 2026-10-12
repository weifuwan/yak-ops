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
    private String id;
    private String hostId;
    private String stateRoot;
    private String ownerIncarnation;
    private LocalDateTime lastSeenTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
