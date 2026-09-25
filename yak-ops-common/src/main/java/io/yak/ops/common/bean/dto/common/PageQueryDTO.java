package io.yak.ops.common.bean.dto.common;

import io.yak.ops.common.constant.CommonConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 统一分页与排序请求参数。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Data
public class PageQueryDTO {

    /** 当前页码，从 1 开始。 */
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于 0")
    private Integer pageNo = CommonConstants.DEFAULT_PAGE_NO;

    /** 每页记录数，最大 200。 */
    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = CommonConstants.MAX_PAGE_SIZE, message = "每页条数不能超过 200")
    private Integer pageSize = CommonConstants.DEFAULT_PAGE_SIZE;

    /** 排序字段，最多 3 个；具体可排序字段由 Repository 白名单决定。 */
    @Valid
    @NotNull(message = "排序条件不能为空")
    @Size(max = CommonConstants.MAX_SORT_FIELDS, message = "排序字段不能超过 3 个")
    private List<SortDTO> sorts = new ArrayList<>();
}
