package io.yak.ops.core.configuration;

/** Typed options describing properties of a submitted pipeline. */
public final class PipelineOptions {

    /** Optional job name for display and diagnostics; it is not a job identity. */
    public static final ConfigOption<String> NAME =
            ConfigOptions.key("pipeline.name")
                    .stringType()
                    .noDefaultValue();

    /**
     * Whether the pipeline compiler may generate IDs for operators lacking
     * explicit stable IDs. If disabled, the compiler must require explicit
     * operator IDs. Stable IDs are recommended for stateful pipelines.
     */
    public static final ConfigOption<Boolean> AUTO_GENERATE_UIDS =
            ConfigOptions.key("pipeline.auto-generate-uids")
                    .booleanType()
                    .defaultValue(true);

    private PipelineOptions() {}
}
