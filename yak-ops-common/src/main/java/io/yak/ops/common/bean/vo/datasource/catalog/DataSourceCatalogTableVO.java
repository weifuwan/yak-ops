package io.yak.ops.common.bean.vo.datasource.catalog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源 Catalog 表、视图、Collection 或 Index 元数据。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceCatalogTableVO {

    /** 所属数据库名称。 */
    private String database;

    /** 所属 Schema 名称；无 Schema 概念的数据源为空。 */
    private String schema;

    /** 表、视图、Collection 或 Index 名称。 */
    private String name;

    /** 元数据类型，例如 TABLE、VIEW、COLLECTION、INDEX。 */
    private String type;

    /** 数据库对象备注。 */
    private String remarks;
}
