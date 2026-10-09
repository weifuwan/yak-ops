package io.yak.ops.core.configuration;

/** Shared execution defaults that do not contain state of a particular job attempt. */
public final class CoreOptions {

    /** Default parallelism for operators without an explicit value; resolved during graph planning. */
    public static final ConfigOption<Integer> DEFAULT_PARALLELISM =
            ConfigOptions.key("parallelism.default").intType().defaultValue(1);

    private CoreOptions() {}
}
