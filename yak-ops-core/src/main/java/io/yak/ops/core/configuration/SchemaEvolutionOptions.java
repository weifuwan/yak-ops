package io.yak.ops.core.configuration;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;

/** Shared opt-in capability used by the Runtime and MySQL CDC Source. */
public final class SchemaEvolutionOptions {

    public static final Option<Boolean> ENABLED =
            Options.key("execution.schema-evolution.enabled").booleanType().defaultValue(false);

    /** Destructive CDC schema changes require a separate, explicit task-level opt-in. */
    public static final Option<Boolean> ALLOW_DROP_COLUMNS = Options.key(
                    "execution.schema-evolution.allow-drop-columns")
            .booleanType()
            .defaultValue(false);

    /** Optional v9 policy, encoded from a strict eight-event matrix. Empty preserves v7/v8 behavior. */
    public static final Option<String> DDL_STRATEGIES = Options.key("execution.schema-evolution.ddl-strategies")
            .stringType()
            .defaultValue("");

    private SchemaEvolutionOptions() {}
}
