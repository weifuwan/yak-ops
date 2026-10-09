package io.yak.ops.connector.jdbc;

import io.yak.ops.core.configuration.ConfigOption;
import io.yak.ops.core.configuration.ConfigOptions;

/** Internal JDBC source planning and fetch options, not product-level runtime controls. */
public final class JdbcSourceOptions {

    public static final ConfigOption<Integer> TARGET_ROWS_PER_SPLIT =
            ConfigOptions.key("connector.jdbc.source.target-rows-per-split").intType().defaultValue(50_000);

    public static final ConfigOption<Integer> MAX_SPLITS_PER_TABLE =
            ConfigOptions.key("connector.jdbc.source.max-splits-per-table").intType().defaultValue(16);

    public static final ConfigOption<Integer> READER_FETCH_BATCH_SIZE =
            ConfigOptions.key("connector.jdbc.source.reader-fetch-batch-size").intType().defaultValue(500);

    public static final ConfigOption<Integer> RESULT_SET_FETCH_SIZE =
            ConfigOptions.key("connector.jdbc.source.result-set-fetch-size").intType().defaultValue(500);

    public static final ConfigOption<Integer> QUERY_TIMEOUT_SECONDS =
            ConfigOptions.key("connector.jdbc.source.query-timeout-seconds").intType().defaultValue(60);

    private JdbcSourceOptions() {}
}
