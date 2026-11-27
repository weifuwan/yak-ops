package org.apache.flink.cdc.connectors.jdbc.reader;

import org.apache.flink.api.connector.source.SourceOutput;
import org.apache.flink.cdc.common.event.CreateTableEvent;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.connector.base.source.reader.RecordEmitter;

import java.util.HashSet;
import java.util.Set;

/** Updates split progress and emits JDBC events into the Flink CDC pipeline. */
public final class JdbcRecordEmitter
        implements RecordEmitter<JdbcRecord, Event, JdbcSourceSplitState> {

    private final Set<TableId> emittedSchemas = new HashSet<>();

    @Override
    public void emitRecord(
            JdbcRecord record, SourceOutput<Event> output, JdbcSourceSplitState splitState) {
        Event event = record.getEvent();
        if (event instanceof CreateTableEvent) {
            if (emittedSchemas.add(((CreateTableEvent) event).tableId())) {
                output.collect(event);
            }
            return;
        }
        if (record.getLastReadValue() != null) {
            splitState.setLastReadValue(record.getLastReadValue());
        }
        output.collect(event);
    }
}
