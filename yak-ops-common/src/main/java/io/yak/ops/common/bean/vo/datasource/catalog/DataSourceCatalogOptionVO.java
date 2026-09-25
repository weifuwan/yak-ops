package io.yak.ops.common.bean.vo.datasource.catalog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Catalog 表选择器的下拉选项。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceCatalogOptionVO {

    /** 选项提交值，通常为表名。 */
    private Object value;

    /** 前端展示名称。 */
    private String label;

    /** 表或视图的补充说明。 */
    private String description;
}
