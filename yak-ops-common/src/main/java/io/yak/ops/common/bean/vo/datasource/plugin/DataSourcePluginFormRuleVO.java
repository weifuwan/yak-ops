package io.yak.ops.common.bean.vo.datasource.plugin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源插件动态表单的字段校验规则。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourcePluginFormRuleVO {

    /** 是否必填。 */
    private Boolean required;

    /** 正则表达式约束。 */
    private String pattern;

    /** 数值或长度最小值。 */
    private Integer min;

    /** 数值或长度最大值。 */
    private Integer max;

    /** 校验失败提示。 */
    private String message;
}
