package io.yak.ops.flow.connector.jdbc.source;

import io.yak.ops.flow.api.checkpoint.CheckpointState;
import io.yak.ops.flow.api.source.SourceSplitEnumerator;
import io.yak.ops.flow.api.trace.RuntimeTraceListener;
import io.yak.ops.flow.connector.jdbc.JdbcNumericSplitConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSourceConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSourceStatistics;
import io.yak.ops.flow.connector.jdbc.JdbcSourceStatisticsReader;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialect;
import io.yak.ops.flow.connector.jdbc.trace.JdbcSourceSplitTraceEvent;
import io.yak.ops.flow.connector.jdbc.trace.JdbcTraceEventType;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC bounded Source 的分片枚举器；可使用显式整数范围，也可按单整数主键的 MIN/MAX/rowCount 动态规划范围。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class JdbcSourceSplitEnumerator implements SourceSplitEnumerator<JdbcSourceSplit> {

    private static final BigInteger ONE = BigInteger.ONE;
    private static final int MAX_DYNAMIC_SPLIT_COUNT = 10_000;

    private final JdbcSourceConfig config;
    private final JdbcConnectionProvider connectionProvider;
    private final JdbcDialect dialect;
    private final RuntimeTraceListener traceListener;
    private List<JdbcSourceSplit> splits;
    private int nextSplitIndex;
    private boolean plannedTraceEmitted;

    JdbcSourceSplitEnumerator(
            JdbcSourceConfig config,
            JdbcConnectionProvider connectionProvider,
            JdbcDialect dialect,
            RuntimeTraceListener traceListener) {
        this.config = config;
        this.connectionProvider = connectionProvider;
        this.dialect = dialect;
        this.traceListener = traceListener;
        if (config.splitConfig() != null) {
            splits = createSplits(config.splitConfig());
        } else if (config.splitSize() == null) {
            splits = wholeTableSplit();
        }
    }

    @Override
    public void start() throws Exception {
        if (splits == null) {
            splits = createDynamicSplits();
        }
        emitPlannedTrace();
    }

    @Override
    public Optional<JdbcSourceSplit> nextSplit() {
        requireStarted();
        if (isFinished()) return Optional.empty();
        return Optional.of(splits.get(nextSplitIndex++));
    }

    @Override
    public boolean isFinished() {
        requireStarted();
        return nextSplitIndex >= splits.size();
    }

    @Override
    public CheckpointState snapshotState(long checkpointId) {
        requireStarted();
        return new JdbcEnumeratorState(nextSplitIndex);
    }

    @Override
    public void restore(CheckpointState state) {
        requireStarted();
        if (!(state instanceof JdbcEnumeratorState jdbcState)) {
            throw new IllegalArgumentException("JDBC Enumerator checkpoint state 类型不匹配");
        }
        if (jdbcState.nextSplitIndex() < 0 || jdbcState.nextSplitIndex() > splits.size()) {
            throw new IllegalArgumentException("JDBC Enumerator checkpoint split index 超出范围");
        }
        nextSplitIndex = jdbcState.nextSplitIndex();
    }

    private List<JdbcSourceSplit> createDynamicSplits() throws Exception {
        Optional<JdbcSourceStatistics> statistics = new JdbcSourceStatisticsReader(connectionProvider)
                .read(config.connection(), config.table(), config.schema(), config.timeoutSeconds());
        if (statistics.isEmpty() || statistics.get().rowCount() <= config.splitSize()) {
            return wholeTableSplit();
        }

        JdbcSourceStatistics sourceStatistics = statistics.get();
        long splitCount = sourceStatistics.rowCount() / config.splitSize();
        if (sourceStatistics.rowCount() % config.splitSize() != 0) splitCount++;
        if (splitCount > MAX_DYNAMIC_SPLIT_COUNT) {
            throw new IllegalArgumentException(
                    "dynamic JDBC split count exceeds " + MAX_DYNAMIC_SPLIT_COUNT + "; increase splitSize");
        }
        return createSplits(new JdbcNumericSplitConfig(
                sourceStatistics.splitColumn(), sourceStatistics.lowerBound(), sourceStatistics.upperBound(), (int)
                        splitCount));
    }

    private List<JdbcSourceSplit> createSplits(JdbcNumericSplitConfig splitConfig) {
        BigInteger lowerBound = BigInteger.valueOf(splitConfig.lowerBound());
        BigInteger upperBound = BigInteger.valueOf(splitConfig.upperBound());
        BigInteger valueCount = upperBound.subtract(lowerBound).add(ONE);
        BigInteger requestedSplitCount = BigInteger.valueOf(splitConfig.splitCount());
        BigInteger rangeSize = valueCount.add(requestedSplitCount).subtract(ONE).divide(requestedSplitCount);

        List<JdbcSourceSplit> result = new ArrayList<>();
        BigInteger currentLowerBound = lowerBound;
        while (currentLowerBound.compareTo(upperBound) <= 0) {
            BigInteger currentUpperBound =
                    currentLowerBound.add(rangeSize).subtract(ONE).min(upperBound);
            result.add(new JdbcSourceSplit(
                    config.table(),
                    splitConfig.column(),
                    currentLowerBound.longValueExact(),
                    currentUpperBound.longValueExact()));
            currentLowerBound = currentUpperBound.add(ONE);
        }
        return List.copyOf(result);
    }

    private List<JdbcSourceSplit> wholeTableSplit() {
        return List.of(new JdbcSourceSplit(config.table()));
    }

    private void emitPlannedTrace() {
        if (plannedTraceEmitted) return;
        for (JdbcSourceSplit split : splits) {
            String sql = split.isRangeSplit()
                    ? dialect.selectRangeSql(split.table(), config.schema(), split.splitColumn())
                    : dialect.selectSql(split.table(), config.schema());
            List<Long> parameters = split.isRangeSplit()
                    ? List.of(split.lowerBoundInclusive(), split.upperBoundInclusive())
                    : List.of();
            traceListener.emit(new JdbcSourceSplitTraceEvent(
                    Instant.now(),
                    JdbcTraceEventType.SOURCE_SPLIT_PLANNED,
                    split.splitId(),
                    null,
                    sql,
                    parameters,
                    split.splitColumn(),
                    split.lowerBoundInclusive(),
                    split.upperBoundInclusive(),
                    0L,
                    0L,
                    null,
                    null,
                    null));
        }
        plannedTraceEmitted = true;
    }

    private void requireStarted() {
        if (splits == null) {
            throw new IllegalStateException("JDBC split enumerator must be started before use");
        }
    }
}
