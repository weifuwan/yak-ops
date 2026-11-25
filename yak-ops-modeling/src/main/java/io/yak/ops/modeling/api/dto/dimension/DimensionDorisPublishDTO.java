package io.yak.ops.modeling.api.dto.dimension;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.dao.entity.enums.DorisPublishModeEnum;
import lombok.Data;

/** Explicit Doris table model and publish strategy selected for one publish attempt. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DimensionDorisPublishDTO {
    private Integer datasourceId;
    private DorisDataModelEnum tableModel;
    private DorisPublishModeEnum publishMode;
    /** Null means automatically adapt to the available Doris backends. */
    private Integer replicationNum;
    private String publishRemark;

    /** Required by the backend when an incremental plan drops existing physical columns. */
    private Boolean confirmDropColumns;

    /** DDL preview hash the user explicitly confirmed for a destructive operation. */
    private String confirmedDdlHash;

    /** Required by the backend for destructive DROP_RECREATE execution. */
    private Boolean confirmDrop;
}