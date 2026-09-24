package io.yak.ops.common.bean.vo.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 兼容数据源设计器的 Catalog 下拉选项。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceCatalogOptionVO {

    private Object value;
    private String label;
    private String description;
}
