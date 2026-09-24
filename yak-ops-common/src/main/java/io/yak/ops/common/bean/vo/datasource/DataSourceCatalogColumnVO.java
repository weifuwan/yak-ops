package io.yak.ops.common.bean.vo.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据源 Catalog 字段元数据。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceCatalogColumnVO {

    private String name;
    private String typeName;
    private Integer jdbcType;
    private Integer size;
    private Integer scale;
    private Boolean nullable;
    private Integer ordinalPosition;
    private Boolean primaryKey;
    private String remarks;
}
