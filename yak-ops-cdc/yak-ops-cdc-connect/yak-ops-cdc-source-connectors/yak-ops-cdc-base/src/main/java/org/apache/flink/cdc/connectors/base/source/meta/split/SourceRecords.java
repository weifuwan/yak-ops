package org.apache.flink.cdc.connectors.base.source.meta.split;

import org.apache.kafka.connect.source.SourceRecord;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Data structure to describe a set of {@link SourceRecord}. */
public final class SourceRecords {

    private final List<SourceRecord> sourceRecords;

    public SourceRecords(List<SourceRecord> sourceRecords) {
        this.sourceRecords = sourceRecords;
    }

    public List<SourceRecord> getSourceRecordList() {
        return sourceRecords;
    }

    public Iterator<SourceRecord> iterator() {
        return sourceRecords.iterator();
    }

    public static SourceRecords fromSingleRecord(SourceRecord record) {
        final List<SourceRecord> records = new ArrayList<>();
        records.add(record);
        return new SourceRecords(records);
    }
}
