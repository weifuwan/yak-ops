package io.yak.ops.core.configuration;

/** Typed options describing stable properties of a submitted pipeline. */
public final class PipelineOptions {

    /** Optional job name for display and diagnostics, not an execution identity. */
    public static final ConfigOption<String> NAME =
            ConfigOptions.key("pipeline.name").stringType().noDefaultValue();

    /**
     * Whether operators without an explicit stable UID may use a generated graph-local ID.
     *
     * <p>Generated IDs are not safe identities for cross-version state recovery. When this
     * option is disabled, every operator must provide a stable UID.
     */
    public static final ConfigOption<Boolean> AUTO_GENERATE_UIDS =
            ConfigOptions.key("pipeline.auto-generate-uids").booleanType().defaultValue(true);

    /**
     * Stable number of key groups used for keyed partitioning.
     *
     * <p>This determines keyed-state identity and must not silently change on recovery.
     */
    public static final ConfigOption<Integer> MAX_PARALLELISM =
            ConfigOptions.key("pipeline.max-parallelism").intType().defaultValue(128);

    private PipelineOptions() {}
}
