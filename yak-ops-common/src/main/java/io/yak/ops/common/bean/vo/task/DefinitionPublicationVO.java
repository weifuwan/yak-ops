package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;
import lombok.Data;

/** Successful publication of a saved parameter version and diagnostics setting. */
@Data
public class DefinitionPublicationVO {
    /** Stable publication receipt identifier. */
    private String id;
    /** Task whose future executions were changed by this publication. */
    private String definitionId;
    /** Immutable parameter version selected by the publication. */
    private Integer version;
    /** Diagnostics setting frozen with the selected parameters. */
    private boolean debug;
    /** Caller-stable request key used to resolve an uncertain HTTP outcome. */
    private String requestId;
    /** Time when publication committed. */
    private LocalDateTime createTime;
    /** Actor who published the task. */
    private String createBy;
}
