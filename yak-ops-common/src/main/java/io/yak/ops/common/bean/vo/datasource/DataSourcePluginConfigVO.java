package io.yak.ops.common.bean.vo.datasource;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据源动态表单配置。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourcePluginConfigVO {

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
    private Boolean installRequired = false;

    private String installHint;

    /** 动态表单分区。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormSectionVO {

        private String key;
        private String title;
        private String description;

        @Builder.Default
        private Boolean collapsible = false;

        @Builder.Default
        private Boolean defaultExpanded = true;

        @Builder.Default
        private List<FormFieldVO> fields = new ArrayList<>();
    }

    /** 动态表单字段。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormFieldVO {

        private String key;
        private String label;

        /**
         * 标准字段组件类型。
         *
         * <p>支持 INPUT、PASSWORD、SELECT、NUMBER、SWITCH、TEXTAREA、CUSTOM_SELECT、DRIVER、SSH 和 JDBC_URL；
         * DRIVER、SSH 与 JDBC_URL 均由前端标准组件负责渲染，不应再通过特定字段 key 触发。
         */
        private String type;

        private String placeholder;
        private Object defaultValue;

        @Builder.Default
        private List<OptionVO> options = new ArrayList<>();

        @Builder.Default
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
        private JdbcUrlLinkageVO urlLinkage;
    }

    /** JDBC URL 联动配置。 */
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

    /** 动态字段显示条件。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VisibilityConditionVO {

        /** 可选；为空时使用 FormFieldVO.dependsOn 中同位置的字段。 */
        private String field;

        /** 支持 EQUALS、NOT_EQUALS、IN、NOT_IN、TRUTHY、FALSY。 */
        private String operator;

        private Object value;

        @Builder.Default
        private List<Object> values = new ArrayList<>();
    }

    /** 下拉选项。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionVO {

        private String label;
        private Object value;
    }

    /** 前端表单校验规则。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleVO {

        private Boolean required;
        private String pattern;
        private Integer min;
        private Integer max;
        private String message;
    }
}
