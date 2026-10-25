package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.plugin.task.datasync.solution.SolutionType;
import java.util.List;
import java.util.Map;

/** One public DATA_SYNC contract; physical Connector settings never enter persisted JSON. */
public final class SolutionOptions {
    public static final Option<SolutionType> SOLUTION_TYPE =
            Options.key("solutionType").enumType(SolutionType.class).noDefaultValue();
    public static final Option<List<String>> STEPS =
            Options.key("steps").listType(String.class).noDefaultValue();
    public static final Option<Map<String, Object>> SOURCE =
            Options.key("source").mapObjectType().noDefaultValue();
    public static final Option<Map<String, Object>> SINK =
            Options.key("sink").mapObjectType().noDefaultValue();
    public static final Option<List<Object>> TRANSFORM =
            Options.key("transform").listType(Object.class).noDefaultValue();
    public static final Option<Map<String, Object>> ENV =
            Options.key("env").mapObjectType().noDefaultValue();

    public static final OptionRule RULE = OptionRule.builder()
            .required(SOLUTION_TYPE, STEPS, SOURCE, SINK, TRANSFORM, ENV)
            .build();

    private SolutionOptions() {}
}
