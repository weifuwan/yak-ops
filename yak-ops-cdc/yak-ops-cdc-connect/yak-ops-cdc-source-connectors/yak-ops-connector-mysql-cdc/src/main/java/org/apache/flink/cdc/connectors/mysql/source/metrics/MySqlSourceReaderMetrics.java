package org.apache.flink.cdc.connectors.mysql.source.metrics;

import org.apache.flink.cdc.connectors.mysql.source.reader.MySqlSourceReader;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.runtime.metrics.MetricNames;

import io.debezium.data.Envelope;
import io.debezium.relational.TableId;
import org.apache.kafka.connect.source.SourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.apache.flink.cdc.connectors.mysql.source.utils.RecordUtils.getTableId;
import static org.apache.flink.cdc.connectors.mysql.source.utils.RecordUtils.isDataChangeRecord;
import static org.apache.flink.cdc.connectors.mysql.source.utils.RecordUtils.isSchemaChangeEvent;

/** A collection class for handling metrics in {@link MySqlSourceReader}. */
public class MySqlSourceReaderMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(MySqlSourceReaderMetrics.class);

    public static final long UNDEFINED = -1;

    private static final String NAMESPACE_GROUP_KEY = "namespace";
    private static final String SCHEMA_GROUP_KEY = "schema";
    private static final String TABLE_GROUP_KEY = "table";

    private static final String NUM_SNAPSHOT_RECORDS = "numSnapshotRecords";
    private static final String NUM_INSERT_DML_RECORDS = "numInsertDMLRecords";
    private static final String NUM_UPDATE_DML_RECORDS = "numUpdateDMLRecords";
    private static final String NUM_DELETE_DML_RECORDS = "numDeleteDMLRecords";
    private static final String NUM_DDL_RECORDS = "numDDLRecords";
    private static final String NUM_RECORDS_IN_ERRORS = "numRecordsInErrors";

    private final MetricGroup metricGroup;
    private final Counter snapshotCounter;
    private final Counter insertCounter;
    private final Counter updateCounter;
    private final Counter deleteCounter;
    private final Counter schemaChangeCounter;
    private final Counter numRecordsInErrorsCounter;
    private final Map<TableId, TableMetrics> tableMetricsMap = new HashMap<>();

    /**
     * currentFetchEventTimeLag = FetchTime - messageTimestamp, where the FetchTime is the time the
     * record fetched into the source operator.
     */
    private volatile long fetchDelay = UNDEFINED;

    public MySqlSourceReaderMetrics(MetricGroup metricGroup) {
        this.metricGroup = metricGroup;
        this.snapshotCounter = metricGroup.counter(NUM_SNAPSHOT_RECORDS);
        this.insertCounter = metricGroup.counter(NUM_INSERT_DML_RECORDS);
        this.updateCounter = metricGroup.counter(NUM_UPDATE_DML_RECORDS);
        this.deleteCounter = metricGroup.counter(NUM_DELETE_DML_RECORDS);
        this.schemaChangeCounter = metricGroup.counter(NUM_DDL_RECORDS);
        this.numRecordsInErrorsCounter = metricGroup.counter(NUM_RECORDS_IN_ERRORS);
    }

    public void registerMetrics() {
        metricGroup.gauge(
                MetricNames.CURRENT_FETCH_EVENT_TIME_LAG, (Gauge<Long>) this::getFetchDelay);
    }

    public long getFetchDelay() {
        return fetchDelay;
    }

    public void recordFetchDelay(long fetchDelay) {
        this.fetchDelay = fetchDelay;
    }

    /** Updates execution-level and table-level CDC counters for an emitted Debezium record. */
    public void updateRecordCounters(SourceRecord record) {
        catchAndWarnLogAllExceptions(() -> {
            if (isDataChangeRecord(record)) {
                recordDataChange(getTableId(record), Envelope.operationFor(record));
            } else if (isSchemaChangeEvent(record)) {
                // Preserve the execution-level DDL observation even if Debezium cannot resolve
                // a physical table identifier for this schema event.
                schemaChangeCounter.inc();
                TableId tableId = getTableId(record);
                if (tableId != null) {
                    getTableMetrics(tableId).markSchemaChangeRecord();
                }
            }
        });
    }

    public void recordDataChange(TableId tableId, Envelope.Operation operation) {
        if (tableId == null || operation == null) {
            return;
        }
        TableMetrics tableMetrics = getTableMetrics(tableId);
        switch (operation) {
            case READ:
                snapshotCounter.inc();
                tableMetrics.markSnapshotRecord();
                break;
            case CREATE:
                insertCounter.inc();
                tableMetrics.markInsertRecord();
                break;
            case UPDATE:
                updateCounter.inc();
                tableMetrics.markUpdateRecord();
                break;
            case DELETE:
                deleteCounter.inc();
                tableMetrics.markDeleteRecord();
                break;
            default:
                // Truncate and message operations are not row-level DML counters.
                break;
        }
    }

    public void recordSchemaChange(TableId tableId) {
        schemaChangeCounter.inc();
        if (tableId != null) {
            getTableMetrics(tableId).markSchemaChangeRecord();
        }
    }

    public void addNumRecordsInErrors(long delta) {
        numRecordsInErrorsCounter.inc(delta);
    }

    private TableMetrics getTableMetrics(TableId tableId) {
        return tableMetricsMap.computeIfAbsent(
                tableId, id -> new TableMetrics(id.catalog(), id.schema(), id.table(), metricGroup));
    }

    private void catchAndWarnLogAllExceptions(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception exception) {
            // Metric failures must never interrupt CDC record processing.
            LOG.warn("Failed to update MySQL CDC source metrics", exception);
        }
    }

    private static class TableMetrics {

        private final Counter snapshotCounter;
        private final Counter insertCounter;
        private final Counter updateCounter;
        private final Counter deleteCounter;
        private final Counter schemaChangeCounter;

        private TableMetrics(
                String namespace, String schema, String table, MetricGroup parentMetricGroup) {
            MetricGroup tableMetricGroup = parentMetricGroup
                    .addGroup(NAMESPACE_GROUP_KEY, processNull(namespace))
                    .addGroup(SCHEMA_GROUP_KEY, processNull(schema))
                    .addGroup(TABLE_GROUP_KEY, processNull(table));
            snapshotCounter = tableMetricGroup.counter(NUM_SNAPSHOT_RECORDS);
            insertCounter = tableMetricGroup.counter(NUM_INSERT_DML_RECORDS);
            updateCounter = tableMetricGroup.counter(NUM_UPDATE_DML_RECORDS);
            deleteCounter = tableMetricGroup.counter(NUM_DELETE_DML_RECORDS);
            schemaChangeCounter = tableMetricGroup.counter(NUM_DDL_RECORDS);
        }

        private void markSnapshotRecord() {
            snapshotCounter.inc();
        }

        private void markInsertRecord() {
            insertCounter.inc();
        }

        private void markUpdateRecord() {
            updateCounter.inc();
        }

        private void markDeleteRecord() {
            deleteCounter.inc();
        }

        private void markSchemaChangeRecord() {
            schemaChangeCounter.inc();
        }

        private static String processNull(String name) {
            return name == null ? "" : name;
        }
    }
}
