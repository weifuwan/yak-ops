package io.yak.ops.common.bean.vo.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据源 Catalog 表元数据。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceCatalogTableVO {

    private String database;
    private String schema;
    private String name;
    private String type;
    private String remarks;
}
