package io.yak.ops.flow.connector.cdc.mysql.source;

import io.yak.ops.flow.api.source.Boundedness;
import io.yak.ops.flow.api.source.Source;
import io.yak.ops.flow.api.source.SourceReader;
import io.yak.ops.flow.api.source.SourceSplitEnumerator;
import io.yak.ops.flow.connector.cdc.mysql.debezium.MySqlCdcSourceReader;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionRuntime;
import java.util.Objects;

/**
 * Debezium-backed MySQL continuous Source，统一输出 YakRow。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public final class MySqlCdcSource implements Source<MySqlCdcSplit> {

    private final MySqlCdcSourceConfig config;
    private final JdbcConnectionRuntime connectionRuntime;

    public MySqlCdcSource(MySqlCdcSourceConfig config) {
        this(config, JdbcConnectionRuntime.getInstance());
    }

    MySqlCdcSource(MySqlCdcSourceConfig config, JdbcConnectionRuntime connectionRuntime) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.connectionRuntime = Objects.requireNonNull(connectionRuntime, "connectionRuntime must not be null");
    }

    @Override
    public Boundedness boundedness() {
        return Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public SourceSplitEnumerator<MySqlCdcSplit> createEnumerator() {
        return new MySqlCdcSplitEnumerator(config.table());
    }

    @Override
    public SourceReader<MySqlCdcSplit> createReader() {
        return new MySqlCdcSourceReader(config, connectionRuntime);
    }
}
