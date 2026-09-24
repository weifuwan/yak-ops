package io.yak.ops.common.bean.vo.datasource;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源插件向前端暴露的动态连接表单配置。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourcePluginConfigVO {

    /** 插件类型，对应 DataSourceDbType。 */
    private String pluginType;

    /**
     * 新版分区表单配置。
     *
     * <p>存在有效 sections 时前端优先按分区渲染；formFields 保留用于兼容旧插件。
     */
    @Builder.Default
    private List<FormSectionVO> sections = new ArrayList<>();

    /** 旧版扁平动态表单字段，保留兼容能力。 */
    @Builder.Default
    private List<FormFieldVO> formFields = new ArrayList<>();

    @Builder.Default
    /** 当前插件是否需要额外安装动作。 */
    private Boolean installRequired = false;

    /** 插件安装或启用提示。 */
    private String installHint;

    /**
     * 动态表单分区。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormSectionVO {

        /** 分区唯一标识。 */
        private String key;

        /** 分区标题。 */
        private String title;

        /** 分区说明。 */
        private String description;

        @Builder.Default
        /** 是否允许折叠。 */
        private Boolean collapsible = false;

        @Builder.Default
        /** 是否默认展开。 */
        private Boolean defaultExpanded = true;

        @Builder.Default
        /** 分区内的动态表单字段。 */
        private List<FormFieldVO> fields = new ArrayList<>();
    }

    /**
     * 单个动态表单字段定义。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormFieldVO {

        /** 字段唯一标识。 */
        private String key;

        /** 前端展示标签。 */
        private String label;

        /**
         * 标准字段组件类型。
         *
         * <p>支持 INPUT、PASSWORD、SELECT、NUMBER、SWITCH、TEXTAREA、CUSTOM_SELECT、DRIVER、SSH 和 JDBC_URL；
         * DRIVER、SSH 与 JDBC_URL 均由前端标准组件负责渲染，不应再通过特定字段 key 触发。
         */
        private String type;

        /** 输入占位提示。 */
        private String placeholder;
        /** 字段默认值。 */
        private Object defaultValue;

        @Builder.Default
        /** SELECT 等字段的可选项。 */
        private List<OptionVO> options = new ArrayList<>();

        @Builder.Default
        /** 前端校验规则。 */
        private List<RuleVO> rules = new ArrayList<>();

        /**
         * 字段联动依赖。visibleWhen 中显式声明的 field 也会被前端自动加入依赖集合。
         */
        @Builder.Default
        private List<String> dependsOn = new ArrayList<>();

        /**
         * 字段显示条件；多个条件使用 AND 语义。condition.field 为空时按顺序映射 dependsOn。
         */
        @Builder.Default
        private List<VisibilityConditionVO> visibleWhen = new ArrayList<>();

        /** JDBC_URL 标准组件的 Host / Port / Database 双向联动描述。 */
        /** JDBC_URL 字段的结构化联动配置。 */
        private JdbcUrlLinkageVO urlLinkage;
    }

    /**
     * JDBC URL 与 Host、Port、Database 字段的双向联动配置。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JdbcUrlLinkageVO {

        /** 例如 jdbc:mysql://{host}:{port}/{database}。 */
        private String template;

        @Builder.Default
        private String hostField = "host";

        @Builder.Default
        private String portField = "port";

        @Builder.Default
        private String databaseField = "database";

        /** 是否在结构化字段变化时保留 ?query / ;properties 尾部参数。 */
        @Builder.Default
        private Boolean preserveSuffix = true;
    }

    /**
     * 动态字段显示条件。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VisibilityConditionVO {

        /** 可选；为空时使用 FormFieldVO.dependsOn 中同位置的字段。 */
        private String field;

        /** 支持 EQUALS、NOT_EQUALS、IN、NOT_IN、TRUTHY、FALSY。 */
        private String operator;

        /** 条件比较值。 */
        private Object value;

        @Builder.Default
        /** IN / NOT_IN 等操作使用的比较值集合。 */
        private List<Object> values = new ArrayList<>();
    }

    /**
     * 动态表单下拉选项。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionVO {

        /** 选项展示名称。 */
        private String label;

        /** 选项提交值。 */
        private Object value;
    }

    /**
     * 前端动态表单校验规则。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleVO {

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
}
