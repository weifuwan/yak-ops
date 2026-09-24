package io.yak.ops.common.bean.vo.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源选择器的下拉选项。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceOptionVO {

    /** 前端展示的数据源名称。 */
    private String label;

    /** 数据源 ID 的字符串形式。 */
    private String value;

    /** 数据库类型。 */
    private String dbType;
}
