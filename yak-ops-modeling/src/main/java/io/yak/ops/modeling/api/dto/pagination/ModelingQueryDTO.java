package io.yak.ops.modeling.api.dto.pagination;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Common query fields for modeling pages. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ModelingQueryDTO extends PaginationBaseDTO {

    private String id;
    private String keyword;
}
