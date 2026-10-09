package io.yak.ops.core.configuration;

/** 描述已提交 Pipeline 属性的类型化配置项。 */
public final class PipelineOptions {

    /** 可选的作业名称，用于展示和诊断，不作为作业身份标识。 */
    public static final ConfigOption<String> NAME =
            ConfigOptions.key("pipeline.name")
                    .stringType()
                    .noDefaultValue();

    /**
     * 是否允许没有显式稳定 UID 的算子使用构图时自动生成的内部 ID。
     * 内部 ID 只适用于当前拓扑，不是跨版本恢复所需的稳定 UID。
     * 如果禁用，所有算子必须显式指定稳定 UID。
     */
    public static final ConfigOption<Boolean> AUTO_GENERATE_UIDS =
            ConfigOptions.key("pipeline.auto-generate-uids")
                    .booleanType()
                    .defaultValue(true);

    /**
     * Stable number of KeyGroups per operator in the current embedded pipeline. This is part of
     * keyed partitioning identity and cannot be silently changed during stateful recovery.
     */
    public static final ConfigOption<Integer> MAX_PARALLELISM =
            ConfigOptions.key("pipeline.max-parallelism")
                    .intType()
                    .defaultValue(128);

    private PipelineOptions() {}
}
