package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;
import lombok.Data;

/** Version history row without the potentially large parameter snapshot. */
@Data
public class DefinitionVersionSummaryVO {
    /** Stable identifier of the owning task. */
    private String definitionId;
    /** Immutable parameter version within the task. */
    private Integer version;
    /** Schema used to interpret the snapshot when its detail is requested. */
    private Integer parameterSchemaVersion;
    /** Time when the parameters were saved. */
    private LocalDateTime createTime;
    /** Actor who saved the parameters. */
    private String createBy;
}
