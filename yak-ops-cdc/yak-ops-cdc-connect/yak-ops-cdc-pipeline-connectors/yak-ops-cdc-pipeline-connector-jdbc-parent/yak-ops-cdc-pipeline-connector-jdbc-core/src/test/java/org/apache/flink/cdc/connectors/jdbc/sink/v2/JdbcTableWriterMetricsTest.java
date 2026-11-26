package org.apache.flink.cdc.connectors.jdbc.sink.v2;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.connectors.jdbc.internal.JdbcOutputFormatBuilder;
import org.apache.flink.cdc.connectors.jdbc.internal.data.JdbcRowData;
import org.apache.flink.cdc.connectors.jdbc.internal.output.RichJdbcOutputFormat;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;

import org.junit.jupiter.api.Test;

class JdbcTableWriterMetricsTest {

    @Test
    void countsRecordsOnlyAfterSuccessfulFlush() throws Exception {
        TableId tableId = TableId.tableId("orders");
        Schema schema = mock(Schema.class);
        JdbcOutputFormatBuilder builder = mock(JdbcOutputFormatBuilder.class);
        RichJdbcOutputFormat outputFormat = mock(RichJdbcOutputFormat.class);
        MetricGroup metricGroup = mock(MetricGroup.class);
        Counter counter = mock(Counter.class);
        when(builder.build(tableId, schema)).thenReturn(outputFormat);
        when(metricGroup.addGroup(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(metricGroup);
        when(metricGroup.counter("numCommittedRecords")).thenReturn(counter);

        JdbcTableWriter writer = new JdbcTableWriter(tableId, schema, builder, metricGroup);
        JdbcRowData row = mock(JdbcRowData.class);
        when(row.getTableId()).thenReturn(tableId);
        when(row.hasPrimaryKey()).thenReturn(true);

        writer.write(row);
        verify(counter, never()).inc(anyLong());

        writer.flush();

        verify(outputFormat).flush();
        verify(counter).inc(1L);
    }

    @Test
    void appendOnlyRecordsAreCountedAfterTheirImmediateFlush() throws Exception {
        TableId tableId = TableId.tableId("orders");
        Schema schema = mock(Schema.class);
        JdbcOutputFormatBuilder builder = mock(JdbcOutputFormatBuilder.class);
        RichJdbcOutputFormat outputFormat = mock(RichJdbcOutputFormat.class);
        MetricGroup metricGroup = mock(MetricGroup.class);
        Counter counter = mock(Counter.class);
        when(builder.build(tableId, schema)).thenReturn(outputFormat);
        when(metricGroup.addGroup(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(metricGroup);
        when(metricGroup.counter("numCommittedRecords")).thenReturn(counter);

        JdbcTableWriter writer = new JdbcTableWriter(tableId, schema, builder, metricGroup);
        JdbcRowData row = mock(JdbcRowData.class);
        when(row.getTableId()).thenReturn(tableId);
        when(row.hasPrimaryKey()).thenReturn(false);

        writer.write(row);

        verify(outputFormat).flush();
        verify(counter).inc(1L);
    }
}
