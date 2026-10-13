package io.yak.ops.dao.entity.task;

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
    /** Latest immutable version number of this definition. */
    private Integer currentVersion;
    /** Task-level detailed diagnostics, independent of executable plugin parameters. */
    private boolean debug;
    /** Optional user-maintained description. */
    private String remark;
    /** Logical deletion timestamp; null while the definition remains active. */
    private LocalDateTime deletedAt;
}
