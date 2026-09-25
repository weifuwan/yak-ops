package io.yak.ops.common.bean.vo.datasource.catalog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源 Catalog 字段元数据。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceCatalogColumnVO {

    /** 字段名称。 */
    private String name;

    /** 数据库原生类型名称。 */
    private String typeName;

    /** JDBC Types 对应的类型编码。 */
    private Integer jdbcType;

    /** 字段长度或精度。 */
    private Integer size;

    /** 小数位数。 */
    private Integer scale;

    /** 是否允许为空。 */
    private Boolean nullable;

    /** 字段在表中的顺序，从数据库元数据返回值开始。 */
    private Integer ordinalPosition;

    /** 是否为主键字段。 */
    private Boolean primaryKey;

    /** 数据库字段备注。 */
    private String remarks;
}
