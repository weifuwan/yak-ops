package io.yak.ops.plugin.task.datasync.metrics;

import io.yak.ops.plugin.task.datasync.DataSyncTableMapping;
import io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public final DataSyncExecutionSnapshot collect(DataSyncExecutionHandle handle)
            throws Exception {
        Map<String, Long> metrics = collectRawMetrics(handle);
        List<DataSyncTableMapping> mappings = handle == null || handle.getTableMappings() == null
                ? Collections.emptyList()
                : handle.getTableMappings();
        return aggregate(metrics, System.currentTimeMillis(), mappings);
    }

    protected abstract Map<String, Long> collectRawMetrics(DataSyncExecutionHandle handle)
            throws Exception;

    private DataSyncExecutionSnapshot aggregate(
            Map<String, Long> metrics, long collectedAt, List<DataSyncTableMapping> mappings) {
        Map<String, Long> availableMetrics = metrics == null ? Collections.emptyMap() : metrics;
        CounterSet summary = new CounterSet();
        Map<String, CounterSet> sourceCounters = new LinkedHashMap<>();
        Map<String, CounterSet> sinkCounters = new LinkedHashMap<>();

        for (Map.Entry<String, Long> entry : availableMetrics.entrySet()) {
            String metricId = entry.getKey();
            if (metricId == null) {
                continue;
            }
            String metricName = metricId.substring(metricId.lastIndexOf('.') + 1);
            String tableId = extractTableIdentifier(metricId);
            if (tableId == null) {
                summary.add(metricName, entry.getValue());
            } else if (COMMITTED.equals(metricName)) {
                sinkCounters.computeIfAbsent(tableId, ignored -> new CounterSet())
                        .add(metricName, entry.getValue());
            } else if (isSourceMetric(metricName)) {
                sourceCounters.computeIfAbsent(tableId, ignored -> new CounterSet())
                        .add(metricName, entry.getValue());
            }
        }

        Long snapshotRecords = firstNonNull(
                summary.value(SNAPSHOT), sumTableValues(sourceCounters, SNAPSHOT));
        Long insertRecords = firstNonNull(
                summary.value(INSERT), sumTableValues(sourceCounters, INSERT));
        Long updateRecords = firstNonNull(
                summary.value(UPDATE), sumTableValues(sourceCounters, UPDATE));
        Long deleteRecords = firstNonNull(
                summary.value(DELETE), sumTableValues(sourceCounters, DELETE));
        Long incrementalRecords = sumIfPresent(insertRecords, updateRecords, deleteRecords);
        Long readRecords = sumIfPresent(snapshotRecords, incrementalRecords);
        Long committedRecords = firstNonNull(
                summary.value(COMMITTED), sumTableValues(sinkCounters, COMMITTED));
        Long ddlEvents = firstNonNull(summary.value(DDL), sumTableValues(sourceCounters, DDL));
        Long errorRecords = firstNonNull(
                summary.value(ERRORS), sumTableValues(sourceCounters, ERRORS));

        List<DataSyncTableMetrics> tables =
                buildTableMetrics(sourceCounters, sinkCounters, mappings);
        String state = snapshotRecords == null || committedRecords == null
                ? "PARTIAL"
                : "AVAILABLE";
        if (availableMetrics.isEmpty()) {
            state = "UNAVAILABLE";
        }

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

    private static List<DataSyncTableMetrics> buildTableMetrics(
            Map<String, CounterSet> sourceCounters,
            Map<String, CounterSet> sinkCounters,
            List<DataSyncTableMapping> mappings) {
        List<DataSyncTableMetrics> tables = new ArrayList<>();
        if (mappings != null && !mappings.isEmpty()) {
            for (DataSyncTableMapping mapping : mappings) {
                if (mapping == null) {
                    continue;
                }
                if (!mapping.isDatabaseScope()) {
                    tables.add(toTableMetrics(
                            mapping.getSourceTable(),
                            mapping.getSinkTable(),
                            findCounters(sourceCounters, mapping.getSourceTable()),
                            findCounters(sinkCounters, mapping.getSinkTable())));
                    continue;
                }

                Map<String, String> resolvedMappings = new LinkedHashMap<>();
                for (String sourceTable : sourceCounters.keySet()) {
                    if (mapping.matchesSourceTable(sourceTable)) {
                        resolvedMappings.put(sourceTable, mapping.resolveSinkTable(sourceTable));
                    }
                }
                for (String sinkTable : sinkCounters.keySet()) {
                    if (mapping.matchesSinkTable(sinkTable)) {
                        resolvedMappings.put(
                                mapping.resolveSourceTable(sinkTable), sinkTable);
                    }
                }
                for (Map.Entry<String, String> resolved : resolvedMappings.entrySet()) {
                    tables.add(toTableMetrics(
                            resolved.getKey(),
                            resolved.getValue(),
                            findCounters(sourceCounters, resolved.getKey()),
                            findCounters(sinkCounters, resolved.getValue())));
                }
            }
            return tables;
        }

        // Compatibility fallback for callers without a task definition: pair metrics only when
        // the source and sink expose the same physical table identifier.
        Set<String> tableIds = new LinkedHashSet<>(sourceCounters.keySet());
        tableIds.addAll(sinkCounters.keySet());
        for (String tableId : tableIds) {
            boolean sourceAvailable = sourceCounters.containsKey(tableId);
            boolean sinkAvailable = sinkCounters.containsKey(tableId);
            tables.add(toTableMetrics(
                    sourceAvailable ? tableId : null,
                    sinkAvailable ? tableId : null,
                    sourceCounters.get(tableId),
                    sinkCounters.get(tableId)));
        }
        return tables;
    }

    private static DataSyncTableMetrics toTableMetrics(
            String sourceTable, String sinkTable, CounterSet source, CounterSet sink) {
        Long tableSnapshot = source == null ? null : source.value(SNAPSHOT);
        Long tableInsert = source == null ? null : source.value(INSERT);
        Long tableUpdate = source == null ? null : source.value(UPDATE);
        Long tableDelete = source == null ? null : source.value(DELETE);
        Long tableIncremental = sumIfPresent(tableInsert, tableUpdate, tableDelete);
        Long tableRead = sumIfPresent(tableSnapshot, tableIncremental);
        Long tableCommitted = sink == null ? null : sink.value(COMMITTED);
        Long tableDdl = source == null ? null : source.value(DDL);

        boolean sourceAvailable = source != null && source.hasAny(
                SNAPSHOT, INSERT, UPDATE, DELETE, DDL, ERRORS);
        boolean sinkAvailable = tableCommitted != null;
        String state = sourceAvailable && sinkAvailable
                ? "AVAILABLE"
                : (sourceAvailable || sinkAvailable ? "PARTIAL" : "UNAVAILABLE");

        return DataSyncTableMetrics.builder()
                .sourceTable(sourceTable)
                .sinkTable(sinkTable)
                .metricsState(state)
                .readRecords(tableRead)
                .snapshotRecords(tableSnapshot)
                .incrementalRecords(tableIncremental)
                .insertRecords(tableInsert)
                .updateRecords(tableUpdate)
                .deleteRecords(tableDelete)
                .committedRecords(tableCommitted)
                .ddlEvents(tableDdl)
                .build();
    }

    private static CounterSet findCounters(Map<String, CounterSet> counters, String table) {
        if (table == null) {
            return null;
        }
        CounterSet exact = counters.get(table);
        if (exact != null) {
            return exact;
        }

        String normalized = normalizeTable(table);
        CounterSet caseInsensitiveMatch = null;
        for (Map.Entry<String, CounterSet> entry : counters.entrySet()) {
            String candidate = entry.getKey();
            if (candidate.equals(table) || candidate.endsWith("." + table)) {
                return entry.getValue();
            }
            String normalizedCandidate = normalizeTable(candidate);
            if (normalizedCandidate.equals(normalized)
                    || normalizedCandidate.endsWith("." + normalized)) {
                if (caseInsensitiveMatch != null && caseInsensitiveMatch != entry.getValue()) {
                    // Do not silently associate two case-distinct physical tables.
                    return null;
                }
                caseInsensitiveMatch = entry.getValue();
            }
        }
        return caseInsensitiveMatch;
    }

    private static String normalizeTable(String table) {
        return table == null ? "" : table.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isSourceMetric(String name) {
        return SNAPSHOT.equals(name) || INSERT.equals(name) || UPDATE.equals(name)
                || DELETE.equals(name) || DDL.equals(name) || ERRORS.equals(name);
    }

    private static Long firstNonNull(Long value, Long fallback) {
        return value == null ? fallback : value;
    }

    private static Long sumTableValues(Map<String, CounterSet> tableCounters, String metricName) {
        boolean found = false;
        long total = 0L;
        for (CounterSet counters : tableCounters.values()) {
            Long value = counters.value(metricName);
            if (value != null) {
                found = true;
                total += value;
            }
        }
        return found ? total : null;
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

        private boolean hasAny(String... names) {
            for (String name : names) {
                if (values.containsKey(name)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isSupported(String name) {
            return SNAPSHOT.equals(name) || INSERT.equals(name) || UPDATE.equals(name)
                    || DELETE.equals(name) || DDL.equals(name) || COMMITTED.equals(name)
                    || ERRORS.equals(name);
        }
    }
}
