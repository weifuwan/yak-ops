package io.yak.ops.common.bean.vo.datasource.plugin;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源插件动态表单的单个字段定义。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourcePluginFormFieldVO {

    /** 字段唯一标识。 */
    private String key;

    /** 前端展示标签。 */
    private String label;

    /** 标准字段组件类型，例如 INPUT、PASSWORD、SELECT、SSH 或 JDBC_URL。 */
    private String type;

    /** 输入占位提示。 */
    private String placeholder;

    /** 字段默认值。 */
    private Object defaultValue;

    /** SELECT 等字段的可选项。 */
    @Builder.Default
    private List<DataSourcePluginFormOptionVO> options = new ArrayList<>();

    /** 前端校验规则。 */
    @Builder.Default
    private List<DataSourcePluginFormRuleVO> rules = new ArrayList<>();

    /** 字段联动依赖；visibleWhen 中声明的字段也属于依赖。 */
    @Builder.Default
    private List<String> dependsOn = new ArrayList<>();

    /** 字段显示条件，多个条件使用 AND 语义。 */
    @Builder.Default
    private List<DataSourcePluginVisibilityConditionVO> visibleWhen = new ArrayList<>();

    /** JDBC_URL 字段的 Host、Port、Database 双向联动配置。 */
    private DataSourcePluginJdbcUrlLinkageVO urlLinkage;
}
