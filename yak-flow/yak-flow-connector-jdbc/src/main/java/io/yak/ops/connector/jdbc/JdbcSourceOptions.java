package io.yak.ops.connector.jdbc;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import java.util.List;

/** Internal JDBC source planning and fetch options, not product-level runtime controls. */
public final class JdbcSourceOptions {

    public static final Option<Integer> TARGET_ROWS_PER_SPLIT =
            option("connector.jdbc.source.target-rows-per-split", 50_000);

    public static final Option<Integer> MAX_SPLITS_PER_TABLE = option("connector.jdbc.source.max-splits-per-table", 16);

    public static final Option<Integer> READER_FETCH_BATCH_SIZE =
            option("connector.jdbc.source.reader-fetch-batch-size", 500);

    public static final Option<Integer> RESULT_SET_FETCH_SIZE =
            option("connector.jdbc.source.result-set-fetch-size", 500);

    public static final Option<Integer> QUERY_TIMEOUT_SECONDS =
            option("connector.jdbc.source.query-timeout-seconds", 60);

    public static final Option<Integer> CONNECTION_ATTEMPTS = option("connector.jdbc.source.connection-attempts", 3);

    /** Product read intent, resolved independently for each selected source table. */
    public static final Option<String> READ_WHERE =
            Options.key("read.where").stringType().noDefaultValue();

    public static final Option<String> SPLIT_KEY =
            Options.key("read.split-key").stringType().noDefaultValue();

    /** Engine-supplied, per-table cursor window; never provided as executable SQL. */
    public static final Option<String> RANGE_MODE =
            Options.key("connector.jdbc.source.range-mode").stringType().noDefaultValue();

    public static final Option<String> RANGE_COLUMN =
            Options.key("connector.jdbc.source.range-column").stringType().noDefaultValue();
    public static final Option<String> RANGE_LOWER =
            Options.key("connector.jdbc.source.range-lower").stringType().noDefaultValue();
    public static final Option<String> RANGE_UPPER =
            Options.key("connector.jdbc.source.range-upper").stringType().noDefaultValue();

    public static io.yak.ops.connector.jdbc.source.JdbcIncrementalRange range(ReadonlyConfig options) {
        if (!options.getSourceMap().containsKey(RANGE_MODE.key())) return null;
        io.yak.ops.connector.jdbc.source.JdbcIncrementalRange.Mode mode =
                io.yak.ops.connector.jdbc.source.JdbcIncrementalRange.Mode.valueOf(options.get(RANGE_MODE));
        String lower = options.getSourceMap().containsKey(RANGE_LOWER.key()) ? options.get(RANGE_LOWER) : null;
        return new io.yak.ops.connector.jdbc.source.JdbcIncrementalRange(
                mode, options.get(RANGE_COLUMN), lower, options.get(RANGE_UPPER));
    }

    private static final List<Option<Integer>> OPTIONS = List.of(
            TARGET_ROWS_PER_SPLIT,
            MAX_SPLITS_PER_TABLE,
            READER_FETCH_BATCH_SIZE,
            RESULT_SET_FETCH_SIZE,
            QUERY_TIMEOUT_SECONDS,
            CONNECTION_ATTEMPTS);

    public static final OptionRule RULE = OptionRule.builder()
            .optional(OPTIONS.toArray(Option<?>[]::new))
            .optional(RANGE_MODE, RANGE_COLUMN, RANGE_LOWER, RANGE_UPPER)
            .optional(READ_WHERE, SPLIT_KEY)
            .build();

    /** Copies source planning and reader settings without copying connection credentials. */
    public static Configuration configuration(ReadonlyConfig options) {
        Configuration result = new Configuration();
        for (Option<Integer> option : OPTIONS) {
            result.set(option, options.get(option));
        }
        return result;
    }

    private JdbcSourceOptions() {}

    private static Option<Integer> option(String name, int value) {
        return Options.key(name).intType().defaultValue(value);
    }
}
