package io.yak.ops.business.datasync.execution.planning;

import io.yak.ops.business.datasync.schema.LogicalColumn;
import io.yak.ops.business.datasync.schema.LogicalTable;
import io.yak.ops.common.bean.vo.datasync.DataSyncOfflineRuntimePlanVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRuntimeConfigVO;
import io.yak.ops.common.enums.datasync.DataSyncRuntimePolicy;
import io.yak.ops.flow.api.row.YakTypeKind;
import io.yak.ops.flow.connector.jdbc.JdbcNumericSplitConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSourceStatistics;
import io.yak.ops.flow.connector.jdbc.JdbcSourceStatisticsReader;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialects;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OFFLINE Execution 的自动运行参数规划器。
 *
 * <p>AUTO 只在根 Execution 创建时计算一次；结果冻结进 definitionSnapshot，后续 Retry Attempt 不重新规划。
 * FIXED 与历史未声明 policy 的配置保持原值。</p>
 *
 * @author weifuwan
 * @since 2026-10-06
 */
public final class OfflineRuntimePlanner {

    private static final Logger LOG = LoggerFactory.getLogger(OfflineRuntimePlanner.class);

    static final long SMALL_TABLE_MAX_ROWS = 100_000L;
    static final long MEDIUM_TABLE_MAX_ROWS = 1_000_000L;
    static final long LARGE_TABLE_MAX_ROWS = 10_000_000L;
    static final long MEDIUM_SPLIT_SIZE = 250_000L;
    static final long LARGE_SPLIT_SIZE = 500_000L;
    static final long HUGE_SPLIT_SIZE = 1_000_000L;
    static final long MAX_SPLIT_SIZE = 10_000_000L;
    static final long TARGET_MAX_SPLIT_COUNT = 1_000L;

    static final int MIN_BATCH_SIZE = 100;
    static final int MAX_BATCH_SIZE = 1_000;
    static final int MYSQL_POSTGRES_BATCH_BYTES = 1024 * 1024;
    static final int ORACLE_BATCH_BYTES = 512 * 1024;

    private final JdbcSourceStatisticsReader statisticsReader;

    public OfflineRuntimePlanner() {
        this(new JdbcSourceStatisticsReader());
    }

    OfflineRuntimePlanner(JdbcSourceStatisticsReader statisticsReader) {
        this.statisticsReader = Objects.requireNonNull(statisticsReader, "statisticsReader must not be null");
    }

    public OfflineRuntimePlan plan(
            DataSourceConnection sourceConnection,
            DataSourceTablePath sourceTable,
            LogicalTable sourceReadTable,
            String targetType,
            DataSyncRuntimeConfigVO taskConfig) {
        Objects.requireNonNull(sourceConnection, "sourceConnection must not be null");
        Objects.requireNonNull(sourceTable, "sourceTable must not be null");
        Objects.requireNonNull(sourceReadTable, "sourceReadTable must not be null");
        Objects.requireNonNull(taskConfig, "taskConfig must not be null");

        DataSyncRuntimePolicy policy =
                taskConfig.getPolicy() == null ? DataSyncRuntimePolicy.FIXED : taskConfig.getPolicy();
        if (policy == DataSyncRuntimePolicy.FIXED) {
            return fixedPlan(sourceReadTable, taskConfig);
        }

        JdbcSourceStatistics statistics = null;
        try {
            statistics = statisticsReader
                    .read(
                            sourceConnection,
                            sourceTable,
                            sourceReadTable.toRuntimeSchema(),
                            requiredPositive(taskConfig.getTimeoutSeconds(), 30))
                    .orElse(null);
        } catch (Exception exception) {
            LOG.warn(
                    "OFFLINE 自动运行参数统计读取失败，回退无分片规划: sourceType={}, table={}",
                    sourceConnection.type(),
                    sourceTable.table());
        }
        return planFromStatistics(sourceReadTable, targetType, taskConfig, statistics);
    }

    OfflineRuntimePlan planFromStatistics(
            LogicalTable sourceReadTable,
            String targetType,
            DataSyncRuntimeConfigVO taskConfig,
            JdbcSourceStatistics statistics) {
        int estimatedRowBytes = estimateRowBytes(sourceReadTable);
        int batchSize = batchSize(estimatedRowBytes, targetType);

        DataSyncRuntimeConfigVO effective = copyConfig(taskConfig);
        effective.setPolicy(DataSyncRuntimePolicy.AUTO);
        effective.setReadBatchSize(batchSize);
        effective.setWriteBatchSize(batchSize);
        effective.setSplitSize(null);
        effective.setSourceParallelism(1);

        DataSyncOfflineRuntimePlanVO summary = new DataSyncOfflineRuntimePlanVO();
        summary.setPolicy(DataSyncRuntimePolicy.AUTO);
        summary.setEstimatedRowBytes(estimatedRowBytes);
        summary.setStatisticsAvailable(statistics != null);
        summary.setSplitCount(1);

        String eligibleSplitColumn = JdbcNumericSplitConfig.eligibleColumn(sourceReadTable.toRuntimeSchema())
                .orElse(null);
        summary.setSplitColumn(eligibleSplitColumn);

        if (statistics == null) {
            return new OfflineRuntimePlan(effective, summary);
        }

        summary.setSourceRowCount(statistics.rowCount());
        summary.setSplitColumn(statistics.splitColumn());

        Long splitSize = splitSize(statistics.rowCount());
        if (splitSize == null) {
            return new OfflineRuntimePlan(effective, summary);
        }

        int splitCount = splitCount(statistics.rowCount(), splitSize);
        effective.setSplitSize(splitSize);
        effective.setSourceParallelism(sourceParallelism(splitCount));
        summary.setSplitCount(splitCount);
        return new OfflineRuntimePlan(effective, summary);
    }

    private OfflineRuntimePlan fixedPlan(LogicalTable sourceReadTable, DataSyncRuntimeConfigVO taskConfig) {
        DataSyncRuntimeConfigVO effective = copyConfig(taskConfig);
        effective.setPolicy(DataSyncRuntimePolicy.FIXED);

        DataSyncOfflineRuntimePlanVO summary = new DataSyncOfflineRuntimePlanVO();
        summary.setPolicy(DataSyncRuntimePolicy.FIXED);
        summary.setEstimatedRowBytes(estimateRowBytes(sourceReadTable));
        summary.setSplitColumn(JdbcNumericSplitConfig.eligibleColumn(sourceReadTable.toRuntimeSchema())
                .orElse(null));
        summary.setStatisticsAvailable(false);
        return new OfflineRuntimePlan(effective, summary);
    }

    static Long splitSize(long rowCount) {
        if (rowCount <= SMALL_TABLE_MAX_ROWS) return null;

        long base = rowCount <= MEDIUM_TABLE_MAX_ROWS
                ? MEDIUM_SPLIT_SIZE
                : rowCount <= LARGE_TABLE_MAX_ROWS ? LARGE_SPLIT_SIZE : HUGE_SPLIT_SIZE;
        long sizeForSplitCap = ceilDiv(rowCount, TARGET_MAX_SPLIT_COUNT);
        return Math.min(MAX_SPLIT_SIZE, Math.max(base, sizeForSplitCap));
    }

    static int splitCount(long rowCount, long splitSize) {
        long count = ceilDiv(rowCount, splitSize);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, count));
    }

    static int sourceParallelism(int splitCount) {
        if (splitCount >= 32) return 8;
        if (splitCount >= 8) return 4;
        if (splitCount >= 2) return 2;
        return 1;
    }

    static int batchSize(int estimatedRowBytes, String targetType) {
        int targetBytes = "ORACLE".equals(JdbcDialects.canonicalType(targetType))
                ? ORACLE_BATCH_BYTES
                : MYSQL_POSTGRES_BATCH_BYTES;
        int rows = targetBytes / Math.max(1, estimatedRowBytes);
        return Math.max(MIN_BATCH_SIZE, Math.min(MAX_BATCH_SIZE, rows));
    }

    static int estimateRowBytes(LogicalTable table) {
        long total = 16L;
        for (LogicalColumn column : table.columns()) {
            total += 8L + estimateColumnBytes(column);
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, total));
    }

    private static int estimateColumnBytes(LogicalColumn column) {
        YakTypeKind kind = column.dataType().kind();
        return switch (kind) {
            case BOOLEAN, TINYINT -> 1;
            case SMALLINT -> 2;
            case INTEGER, FLOAT, DATE -> 4;
            case BIGINT, DOUBLE, TIME, TIMESTAMP -> 8;
            case TIMESTAMP_WITH_TIME_ZONE, DECIMAL -> 16;
            case STRING -> boundedVariableBytes(column.length(), true);
            case BINARY -> boundedVariableBytes(column.length(), false);
        };
    }

    private static int boundedVariableBytes(Integer length, boolean character) {
        long capacity = length == null ? 128L : Math.max(16L, length.longValue());
        long estimated = character ? capacity * 2L : capacity;
        return (int) Math.min(2_048L, Math.max(16L, estimated));
    }

    private static DataSyncRuntimeConfigVO copyConfig(DataSyncRuntimeConfigVO source) {
        DataSyncRuntimeConfigVO target = new DataSyncRuntimeConfigVO();
        target.setPolicy(source.getPolicy());
        target.setFetchSize(requiredPositive(source.getFetchSize(), 500));
        target.setReadBatchSize(requiredPositive(source.getReadBatchSize(), 500));
        target.setWriteBatchSize(requiredPositive(source.getWriteBatchSize(), 500));
        target.setSplitSize(source.getSplitSize());
        target.setSourceParallelism(requiredPositive(source.getSourceParallelism(), 1));
        target.setTimeoutSeconds(requiredPositive(source.getTimeoutSeconds(), 30));
        return target;
    }

    private static int requiredPositive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static long ceilDiv(long value, long divisor) {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }
}
