package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Requests a complete, filtered page of authorized physical table metadata. */
@Getter
@Setter
public class DataSourceTablePageDTO extends DataSourceCatalogQueryDTO {
    /** Full-match regular expression for database names, or schema names without catalogs. */
    @Size(max = 256, message = "库名表达式不能超过 256 个字符")
    private String databasePattern;

    /** Full-match regular expression for physical table names. */
    @Size(max = 256, message = "表名表达式不能超过 256 个字符")
    private String tablePattern;

    /** Number of matching entries preceding this page in stable identifier order. */
    @Min(value = 0, message = "目录偏移量不能小于 0")
    @Max(value = 1000000, message = "目录偏移量超过查询范围")
    private int offset;
}
