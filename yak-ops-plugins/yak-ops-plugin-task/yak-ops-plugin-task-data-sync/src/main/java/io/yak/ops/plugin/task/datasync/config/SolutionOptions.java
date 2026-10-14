package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.plugin.task.datasync.solution.SolutionType;
import java.util.List;
import java.util.Map;

/** Public version-three task options; physical connector settings remain engine-owned. */
public final class SolutionOptions {
    public static final Option<SolutionType> SOLUTION_TYPE =
            Options.key("solutionType").enumType(SolutionType.class).noDefaultValue();
    public static final Option<List<String>> STEPS =
            Options.key("steps").listType(String.class).noDefaultValue();
    public static final Option<Map<String, Object>> TABLE_SELECTION =
            Options.key("tableSelection").mapObjectType().noDefaultValue();
    public static final Option<String> SELECTION_MODE =
            Options.key("mode").stringType().noDefaultValue();
    public static final Option<List<Object>> TABLE_MAPPINGS =
            Options.key("tableMappings").listType(Object.class).noDefaultValue();

    public static final OptionRule RULE = OptionRule.builder()
            .required(SOLUTION_TYPE, STEPS, JobOptions.SOURCE, JobOptions.SINK, TABLE_SELECTION, TABLE_MAPPINGS)
            .optional(JobOptions.TRANSFORM)
            .build();
    public static final OptionRule CONNECTION_RULE = OptionRule.builder()
            .required(
                    JobOptions.DATASOURCE_ID,
                    Conditions.notBlank(JobOptions.DATASOURCE_ID)
                            .and(Conditions.matches(JobOptions.DATASOURCE_ID, "(?s)^.{1,64}$")))
            .build();
    public static final OptionRule SELECTION_RULE =
            OptionRule.builder().required(SELECTION_MODE).build();
    public static final Option<Map<String, Object>> TARGET =
            Options.key("target").mapObjectType().noDefaultValue();
    public static final OptionRule TABLE_MAPPING_RULE = OptionRule.builder()
            .required(JobOptions.SOURCE, TARGET, JdbcSinkOptions.WRITE_MODE)
            .build();
    public static final OptionRule TABLE_RULE = OptionRule.builder()
            .required(
                    JobOptions.TABLE,
                    Conditions.notBlank(JobOptions.TABLE).and(Conditions.matches(JobOptions.TABLE, "(?s)^.{1,128}$")))
            .optional(JobOptions.DATABASE, Conditions.matches(JobOptions.DATABASE, "(?s)^.{0,128}$"))
            .optional(JobOptions.SCHEMA, Conditions.matches(JobOptions.SCHEMA, "(?s)^.{0,128}$"))
            .build();

    private SolutionOptions() {}
}
