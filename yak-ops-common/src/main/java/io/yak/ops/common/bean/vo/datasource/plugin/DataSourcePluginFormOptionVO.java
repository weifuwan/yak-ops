package io.yak.ops.common.bean.vo.datasource.plugin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源插件动态表单的下拉选项。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourcePluginFormOptionVO {

    /** 选项展示名称。 */
    private String label;

    /** 选项提交值。 */
    private Object value;
}
