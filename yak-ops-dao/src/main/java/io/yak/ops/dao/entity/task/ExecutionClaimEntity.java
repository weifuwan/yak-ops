package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Database-enforced exclusive execution resource owned by an Instance. */
@Getter
@Setter
@ToString
@TableName("yak_ops_task_execution_claim")
public class ExecutionClaimEntity extends BaseEntity {
    /** Workspace in which this exclusive resource is identified. */
    private String workspaceId;
    /** Category of resource requiring exclusive execution. */
    private String resourceType;
    /** Canonical case-sensitive resource identity without credentials. */
    private String resourceKey;
    /** Business Instance that exclusively owns this claim. */
    private String instanceId;
}
