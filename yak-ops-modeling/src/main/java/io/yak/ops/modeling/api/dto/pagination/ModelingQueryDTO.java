package io.yak.ops.modeling.api.dto.pagination;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Common query fields for modeling pages. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class ModelingQueryDTO extends PaginationBaseDTO {

    private String id;
    private String keyword;
}
