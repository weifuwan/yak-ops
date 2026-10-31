package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Stable Task Definition identity; executable parameters live only in version records. */
@Getter
@Setter
@ToString
@TableName("yak_ops_task_definition")
public class DefinitionEntity extends BaseEntity {
    /** Workspace identifier enforcing definition ownership and isolation. */
    private String workspaceId;
    /** Definition name within its Workspace. */
    private String name;
    /** Registered plugin type; executable payload lives in version records. */
    private String taskType;
    /** Publication status independent of execution state. */
    private DefinitionStatus status;
    /** Latest saved immutable parameter version, independent of publication. */
    private Integer currentVersion;
    /** Monotonic task edit/publication revision used to reject stale writes. */
    private long revision;
    /** Parameter version selected for future execution; null while unpublished. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer publishedVersion;
    /** Diagnostics frozen with the published version; null while unpublished. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean publishedDebug;
    /** Saved diagnostics setting, applied to future executions only after publication. */
    private boolean debug;
    /** Optional user-maintained description. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    /** Logical deletion timestamp; null while the definition remains active. */
    private LocalDateTime deletedAt;
}
