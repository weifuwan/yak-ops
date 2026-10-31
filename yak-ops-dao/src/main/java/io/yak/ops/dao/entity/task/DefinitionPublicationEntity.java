package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Append-only receipt of a successful task publication, including its idempotency intent. */
@Getter
@Setter
@TableName("yak_ops_task_publication")
public class DefinitionPublicationEntity extends BaseEntity {
    /** Workspace owning both the task and this publication. */
    private String workspaceId;
    /** Stable task identity whose publication changed. */
    private String definitionId;
    /** Immutable parameter version selected by this publication. */
    private Integer version;
    /** Saved diagnostics setting frozen when publication committed. */
    private boolean debug;
    /** Edit revision supplied by the original request, identifying its saved diagnostics. */
    private long sourceRevision;
    /** Case-sensitive caller request key, unique within the Workspace and task. */
    private String requestId;
}
