package io.yak.ops.common.bean.vo.task;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.LocalDateTime;
import lombok.Data;

/** Immutable executable version for a future Workflow node reference. */
@Data
public class DefinitionVersionVO {
    /** Stable identifier of the owning definition. */
    private String definitionId;
    /** Immutable version number usable by future Workflow nodes. */
    private Integer version;
    /** Plugin parameter schema version frozen with this definition version. */
    private Integer parameterSchemaVersion;
    /** Persisted plugin-specific JSON snapshot for this version. */
    @JsonRawValue
    private String parameters;
    /** Timestamp when the immutable version was created. */
    private LocalDateTime createTime;
    /** Actor who saved this immutable parameter snapshot. */
    private String createBy;
}
