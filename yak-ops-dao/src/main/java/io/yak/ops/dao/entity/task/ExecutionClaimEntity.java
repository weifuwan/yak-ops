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
    private String workspaceId;
    private String resourceType;
    private String resourceKey;
    private String instanceId;
}
