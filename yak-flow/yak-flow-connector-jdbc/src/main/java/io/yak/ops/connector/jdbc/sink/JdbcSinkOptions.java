package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import java.util.List;

/** Supported JDBC Sink write modes supplied by a persisted Connector definition. */
public final class JdbcSinkOptions {

    public static final Option<JdbcWriteMode> WRITE_MODE = Options.key("writeMode")
            .singleChoice(JdbcWriteMode.class, List.of(JdbcWriteMode.values()))
            .noDefaultValue();

    public static final Option<String> PRE_SQL =
            Options.key("write.pre-sql").stringType().noDefaultValue();
    public static final Option<String> POST_SQL =
            Options.key("write.post-sql").stringType().noDefaultValue();
    public static final Option<Integer> BATCH_SIZE =
            Options.key("write.batch-size").intType().defaultValue(500);

    /** Physical opt-in; callers must establish that target tables can be reordered independently. */
    public static final Option<JdbcBatchOrder> BATCH_ORDER = Options.key("connector.jdbc.sink.batch-order")
            .singleChoice(JdbcBatchOrder.class, List.of(JdbcBatchOrder.values()))
            .defaultValue(JdbcBatchOrder.INPUT_ORDER);

    /** Maximum live PreparedStatements per Writer, across all table routes and mutation kinds. */
    public static final Option<Integer> STATEMENT_CACHE_SIZE =
            Options.key("connector.jdbc.sink.statement-cache-size").intType().defaultValue(64);

    /** Opt-in to XA after a versioned product configuration selection. */
    public static final Option<Boolean> XA_ENABLED =
            Options.key("connector.jdbc.sink.xa-enabled").booleanType().defaultValue(false);

    /** Stable task Instance identity assigned by the Runtime planner, never by user input. */
    public static final Option<String> XA_INSTANCE_ID =
            Options.key("connector.jdbc.sink.xa-instance").stringType().noDefaultValue();

    private JdbcSinkOptions() {}
}
