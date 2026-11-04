package io.yak.ops.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "QUERY-PAGE-INFO")
@Data
public class PageQueryDto {

    @Schema(example = "10", required = true)
    private Integer pageSize = 10;

    @Schema(example = "1", required = true)
    private Integer pageNo = 1;
}
