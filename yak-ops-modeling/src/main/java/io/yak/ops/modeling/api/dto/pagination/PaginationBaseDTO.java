package io.yak.ops.modeling.api.dto.pagination;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Shared pagination request for migrated modeling endpoints.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaginationBaseDTO {

    @NotNull
    private Integer pageNo = 1;

    @NotNull
    private Integer pageSize = 10;
}
