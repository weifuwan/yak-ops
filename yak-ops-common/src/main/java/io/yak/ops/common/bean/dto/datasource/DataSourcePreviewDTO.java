package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Describes an unsaved source-table read preview; it never identifies a task execution. */
@Getter
@Setter
public class DataSourcePreviewDTO extends DataSourceTablePathDTO {
    /** Optional database predicate without the WHERE keyword. */
    @Size(max = 16384, message = "数据过滤条件不能超过 16384 个字符")
    private String where;

    /** Maximum returned rows; the service reads one additional row to detect truncation. */
    @Min(value = 1, message = "预览行数必须大于 0")
    @Max(value = 100, message = "预览最多返回 100 行")
    private int limit = 20;
}
