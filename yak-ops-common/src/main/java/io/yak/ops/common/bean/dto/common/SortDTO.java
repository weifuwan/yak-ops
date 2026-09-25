package io.yak.ops.common.bean.dto.common;

import io.yak.ops.common.enums.common.SortDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 单个 API 字段排序参数。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Data
public class SortDTO {

    /** API camelCase 字段名；Repository 必须再次通过白名单映射。 */
    @NotBlank(message = "排序字段不能为空")
    @Size(max = 64, message = "排序字段长度不能超过 64")
    @Pattern(regexp = "^[a-z][A-Za-z0-9]*$", message = "排序字段必须是合法的 camelCase API 字段")
    private String field;

    /** 排序方向，只允许 ASC 或 DESC。 */
    @NotNull(message = "排序方向不能为空")
    private SortDirection direction;
}
