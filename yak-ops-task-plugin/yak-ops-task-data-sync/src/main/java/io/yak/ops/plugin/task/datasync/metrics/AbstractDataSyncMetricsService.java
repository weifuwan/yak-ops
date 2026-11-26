package io.yak.ops.plugin.task.datasync.metrics;

import io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionHandle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared aggregation rules for Flink metric identifiers. */
public abstract class AbstractDataSyncMetricsService implements DataSyncMetricsService {

    private static final String SNAPSHOT = "numSnapshotRecords";
    private static final String INSERT = "numInsertDMLRecords";
    private static final String UPDATE = "numUpdateDMLRecords";
    private static final String DELETE = "numDeleteDMLRecords";
    private static final String DDL = "numDDLRecords";
    private static final String COMMITTED = "numCommittedRecords";
    private static final String ERRORS = "numRecordsInErrors";

    @Override
    public final DataSyncExecutionSnapshot collect(DataSyncExecutionHandle handle) throws Exception {
        Map<String, Long> metrics = collectRawMetrics(handle);
        return aggregate(metrics, System.currentTimeMillis());
    }

    protected abstract Map<String, Long> collectRawMetrics(DataSyncExecutionHandle handle) throws Exception;

    private DataSyncExecutionSnapshot aggregate(Map<String, Long> metrics, long collectedAt) {
        if (metrics == null || metrics.isEmpty()) {
            return DataSyncExecutionSnapshot.builder()
                    .collectedAt(collectedAt)
                    .metricsState("UNAVAILABLE")
                    .tables(new ArrayList<>())
                    .build();
        }

        CounterSet summary = new CounterSet();
        Map<String, CounterSet> tableCounters = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : metrics.entrySet()) {
            String metricId = entry.getKey();
            String metricName = metricId.substring(metricId.lastIndexOf('.') + 1);
            String tableId = extractTableIdentifier(metricId);
            CounterSet target = tableId == null ? summary
                    : tableCounters.computeIfAbsent(tableId, ignored -> new CounterSet());
            target.add(metricName, entry.getValue());
        }

        Long snapshotRecords = summary.value(SNAPSHOT);
        Long insertRecords = summary.value(INSERT);
        Long updateRecords = summary.value(UPDATE);
        Long deleteRecords = summary.value(DELETE);
        Long incrementalRecords = sumIfPresent(insertRecords, updateRecords, deleteRecords);
        Long readRecords = sumIfPresent(snapshotRecords, incrementalRecords);
        Long committedRecords = summary.value(COMMITTED);
        Long ddlEvents = summary.value(DDL);
        Long errorRecords = summary.value(ERRORS);

        List<DataSyncTableMetrics> tables = new ArrayList<>();
        for (Map.Entry<String, CounterSet> entry : tableCounters.entrySet()) {
            CounterSet counters = entry.getValue();
            Long tableSnapshot = counters.value(SNAPSHOT);
            Long tableInsert = counters.value(INSERT);
            Long tableUpdate = counters.value(UPDATE);
            Long tableDelete = counters.value(DELETE);
            Long tableIncremental = sumIfPresent(tableInsert, tableUpdate, tableDelete);
            Long tableRead = sumIfPresent(tableSnapshot, tableIncremental);
            Long tableCommitted = counters.value(COMMITTED);
            boolean sourceMetric = tableRead != null || counters.value(DDL) != null;
            boolean sinkMetric = tableCommitted != null;
            tables.add(DataSyncTableMetrics.builder()
                    .sourceTable(sourceMetric ? entry.getKey() : null)
                    .sinkTable(sinkMetric ? entry.getKey() : null)
                    .readRecords(tableRead)
                    .snapshotRecords(tableSnapshot)
                    .incrementalRecords(tableIncremental)
                    .insertRecords(tableInsert)
                    .updateRecords(tableUpdate)
                    .deleteRecords(tableDelete)
                    .committedRecords(tableCommitted)
                    .ddlEvents(counters.value(DDL))
                    .build());
        }

        String state = snapshotRecords == null || committedRecords == null ? "PARTIAL" : "AVAILABLE";
        return DataSyncExecutionSnapshot.builder()
                .readRecords(readRecords)
                .snapshotRecords(snapshotRecords)
                .incrementalRecords(incrementalRecords)
                .insertRecords(insertRecords)
                .updateRecords(updateRecords)
                .deleteRecords(deleteRecords)
                .committedRecords(committedRecords)
                .ddlEvents(ddlEvents)
                .errorRecords(errorRecords)
                .collectedAt(collectedAt)
                .metricsState(state)
                .tables(tables)
                .build();
    }

    private static Long sumIfPresent(Long... values) {
        boolean found = false;
        long total = 0L;
        for (Long value : values) {
            if (value != null) {
                found = true;
                total += value;
            }
        }
        return found ? total : null;
    }

    private static String extractTableIdentifier(String metricId) {
        int tableIndex = metricId.lastIndexOf(".table.");
        if (tableIndex < 0) {
            return null;
        }
        String prefix = metricId.substring(0, tableIndex);
        int namespaceIndex = prefix.lastIndexOf(".namespace.");
        int schemaIndex = prefix.lastIndexOf(".schema.");
        if (namespaceIndex < 0 || schemaIndex < namespaceIndex) {
            return null;
        }
        String namespace = prefix.substring(namespaceIndex + ".namespace.".length(), schemaIndex);
        String schema = prefix.substring(schemaIndex + ".schema.".length());
        String table = metricId.substring(tableIndex + ".table.".length());
        int suffix = table.indexOf('.');
        if (suffix >= 0) {
            table = table.substring(0, suffix);
        }
        List<String> parts = new ArrayList<>();
        if (!namespace.isEmpty()) {
            parts.add(namespace);
        }
        if (!schema.isEmpty()) {
            parts.add(schema);
        }
        if (!table.isEmpty()) {
            parts.add(table);
        }
        return parts.isEmpty() ? null : String.join(".", parts);
    }

    private static final class CounterSet {

        private final Map<String, Long> values = new HashMap<>();

        private void add(String name, Long value) {
            if (isSupported(name) && value != null) {
                values.merge(name, value, Long::sum);
            }
        }

        private Long value(String name) {
            return values.get(name);
        }

        private boolean isSupported(String name) {
            return SNAPSHOT.equals(name) || INSERT.equals(name) || UPDATE.equals(name)
                    || DELETE.equals(name) || DDL.equals(name) || COMMITTED.equals(name)
                    || ERRORS.equals(name);
        }
    }
}
