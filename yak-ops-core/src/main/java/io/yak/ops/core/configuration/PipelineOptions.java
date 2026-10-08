package io.yak.ops.core.configuration;

/** 描述已提交 Pipeline 属性的类型化配置项。 */
public final class PipelineOptions {

    /** 可选的作业名称，用于展示和诊断，不作为作业身份标识。 */
    public static final ConfigOption<String> NAME =
            ConfigOptions.key("pipeline.name")
                    .stringType()
                    .noDefaultValue();

    /**
     * 是否允许 Pipeline 编译器为未指定稳定 ID 的算子自动生成 ID。
     * 如果禁用，编译器必须要求算子显式指定 ID。
     * 有状态 Pipeline 建议使用稳定的算子 ID。
     */
    public static final ConfigOption<Boolean> AUTO_GENERATE_UIDS =
            ConfigOptions.key("pipeline.auto-generate-uids")
                    .booleanType()
                    .defaultValue(true);

    private PipelineOptions() {}
}
