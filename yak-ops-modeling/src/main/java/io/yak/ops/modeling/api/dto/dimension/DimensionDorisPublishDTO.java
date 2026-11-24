package io.yak.ops.modeling.api.dto.dimension;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** Target datasource and database for DDL preview or physical publish. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DimensionDorisPublishDTO {
    private Integer datasourceId;
    private String targetDatabase;
}
