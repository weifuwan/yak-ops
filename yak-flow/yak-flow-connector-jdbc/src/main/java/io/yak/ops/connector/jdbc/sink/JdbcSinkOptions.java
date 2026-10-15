package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import java.util.List;

/** Supported JDBC Sink write modes supplied by a persisted Connector definition. */
public final class JdbcSinkOptions {

    public static final Option<JdbcWriteMode> WRITE_MODE = Options.key("writeMode")
            .singleChoice(JdbcWriteMode.class, List.of(JdbcWriteMode.APPEND, JdbcWriteMode.UPSERT))
            .noDefaultValue();

    /** Physical opt-in; callers must establish that target tables can be reordered independently. */
    public static final Option<JdbcBatchOrder> BATCH_ORDER = Options.key("connector.jdbc.sink.batch-order")
            .singleChoice(JdbcBatchOrder.class, List.of(JdbcBatchOrder.values()))
            .defaultValue(JdbcBatchOrder.INPUT_ORDER);

    /** Maximum live PreparedStatements per Writer, across all table routes and mutation kinds. */
    public static final Option<Integer> STATEMENT_CACHE_SIZE =
            Options.key("connector.jdbc.sink.statement-cache-size").intType().defaultValue(64);

    private JdbcSinkOptions() {}
}
