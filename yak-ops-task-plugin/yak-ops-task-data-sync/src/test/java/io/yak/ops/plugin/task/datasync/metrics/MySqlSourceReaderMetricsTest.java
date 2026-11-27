package io.yak.ops.plugin.task.datasync.metrics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.flink.cdc.connectors.mysql.source.metrics.MySqlSourceReaderMetrics;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;

import org.junit.jupiter.api.Test;

import io.debezium.data.Envelope;
import io.debezium.relational.TableId;

class MySqlSourceReaderMetricsTest {

    @Test
    void recordsExecutionAndTableLevelSnapshotAndDmlCounters() {
        MetricGroup root = mock(MetricGroup.class);
        MetricGroup namespace = mock(MetricGroup.class);
        MetricGroup schema = mock(MetricGroup.class);
        MetricGroup table = mock(MetricGroup.class);

        Counter executionSnapshot = mock(Counter.class);
        Counter executionInsert = mock(Counter.class);
        Counter executionUpdate = mock(Counter.class);
        Counter executionDelete = mock(Counter.class);
        Counter executionDdl = mock(Counter.class);
        Counter tableSnapshot = mock(Counter.class);
        Counter tableInsert = mock(Counter.class);
        Counter tableUpdate = mock(Counter.class);
        Counter tableDelete = mock(Counter.class);
        Counter tableDdl = mock(Counter.class);

        when(root.counter("numSnapshotRecords")).thenReturn(executionSnapshot);
        when(root.counter("numInsertDMLRecords")).thenReturn(executionInsert);
        when(root.counter("numUpdateDMLRecords")).thenReturn(executionUpdate);
        when(root.counter("numDeleteDMLRecords")).thenReturn(executionDelete);
        when(root.counter("numDDLRecords")).thenReturn(executionDdl);
        when(root.addGroup("namespace", "source_db")).thenReturn(namespace);
        when(namespace.addGroup("schema", "")).thenReturn(schema);
        when(schema.addGroup("table", "orders")).thenReturn(table);
        when(table.counter("numSnapshotRecords")).thenReturn(tableSnapshot);
        when(table.counter("numInsertDMLRecords")).thenReturn(tableInsert);
        when(table.counter("numUpdateDMLRecords")).thenReturn(tableUpdate);
        when(table.counter("numDeleteDMLRecords")).thenReturn(tableDelete);
        when(table.counter("numDDLRecords")).thenReturn(tableDdl);

        MySqlSourceReaderMetrics metrics = new MySqlSourceReaderMetrics(root);
        TableId tableId = new TableId("source_db", null, "orders");

        metrics.recordDataChange(tableId, Envelope.Operation.READ);
        metrics.recordDataChange(tableId, Envelope.Operation.CREATE);
        metrics.recordDataChange(tableId, Envelope.Operation.UPDATE);
        metrics.recordDataChange(tableId, Envelope.Operation.DELETE);
        metrics.recordSchemaChange(tableId);

        verify(executionSnapshot).inc();
        verify(executionInsert).inc();
        verify(executionUpdate).inc();
        verify(executionDelete).inc();
        verify(executionDdl).inc();
        verify(tableSnapshot).inc();
        verify(tableInsert).inc();
        verify(tableUpdate).inc();
        verify(tableDelete).inc();
        verify(tableDdl).inc();
    }
}
