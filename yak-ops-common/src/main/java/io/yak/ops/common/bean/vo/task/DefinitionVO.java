package io.yak.ops.common.bean.vo.task;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.LocalDateTime;
import lombok.Data;

/** Workspace-scoped generic Task Definition with its current validated parameters. */
@Data
public class DefinitionVO {
    /** Stable Task Definition identifier. */
    private String id;
    /** Definition name within its Workspace. */
    private String name;
    /** Registered plugin type used by the current version. */
    private String taskType;
    /** Publication state, independent of execution status. */
    private String status;
    /** Current immutable definition version number. */
    private Integer definitionVersion;
    /** Revision required when saving or publishing this task. */
    private long revision;
    /** Parameter version used for future executions; null while unpublished. */
    private Integer publishedVersion;
    /** Diagnostics setting frozen by publication; null while unpublished. */
    private Boolean publishedDebug;
    /** Whether saved parameters or diagnostics differ from the current publication. */
    private boolean hasUnpublishedChanges;
    /** Schema version used by the plugin to interpret parameters. */
    private Integer parameterSchemaVersion;
    /** Validated plugin-specific JSON for the current definition version. */
    @JsonRawValue
    private String parameters;
    /** Detailed diagnostics for newly submitted Instances. */
    private boolean debug;
    /** Optional human-readable description. */
    private String remark;
    /** Definition creation timestamp. */
    private LocalDateTime createTime;
    /** Timestamp of the latest definition edit. */
    private LocalDateTime updateTime;
    /** Identifier of the operator who last modified the definition. */
    private String updateBy;
}
