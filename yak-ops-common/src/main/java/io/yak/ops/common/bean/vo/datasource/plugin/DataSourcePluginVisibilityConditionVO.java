package io.yak.ops.common.bean.vo.datasource.plugin;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源插件动态字段的显示条件。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourcePluginVisibilityConditionVO {

    /** 依赖字段；为空时按 dependsOn 中对应位置解析。 */
    private String field;

    /** 条件操作符，例如 EQUALS、NOT_EQUALS、IN、NOT_IN、TRUTHY、FALSY。 */
    private String operator;

    /** 单值比较参数。 */
    private Object value;

    /** IN / NOT_IN 等操作使用的比较值集合。 */
    @Builder.Default
    private List<Object> values = new ArrayList<>();
}
