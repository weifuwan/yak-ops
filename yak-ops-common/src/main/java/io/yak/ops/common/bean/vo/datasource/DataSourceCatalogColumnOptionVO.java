package io.yak.ops.common.bean.vo.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 兼容数据源设计器的字段选项。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceCatalogColumnOptionVO {

    private Integer key;
    private Object fieldName;
    private Object fieldType;
    private Integer ordinalPosition;
    private String isNullable;
    private String fieldComment;
    private String fieldKey;
}
