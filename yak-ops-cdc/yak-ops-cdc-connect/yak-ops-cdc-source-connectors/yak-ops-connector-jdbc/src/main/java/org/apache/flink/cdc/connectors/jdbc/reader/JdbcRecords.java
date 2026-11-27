package org.apache.flink.cdc.connectors.jdbc.reader;

import org.apache.flink.connector.base.source.reader.RecordsWithSplitIds;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** A batch of records belonging to one JDBC split. */
final class JdbcRecords implements RecordsWithSplitIds<JdbcRecord> {

    private String splitId;
    private final Iterator<JdbcRecord> recordsForSplit;
    private Iterator<JdbcRecord> currentRecords;
    private final Set<String> finishedSplits;

    private JdbcRecords(
            String splitId, Iterator<JdbcRecord> recordsForSplit, Set<String> finishedSplits) {
        this.splitId = splitId;
        this.recordsForSplit = recordsForSplit;
        this.finishedSplits = finishedSplits;
    }

    static JdbcRecords forRecords(String splitId, List<JdbcRecord> records) {
        return new JdbcRecords(splitId, records.iterator(), Collections.emptySet());
    }

    static JdbcRecords finished(String splitId) {
        return new JdbcRecords(null, Collections.emptyIterator(), Collections.singleton(splitId));
    }

    static JdbcRecords empty() {
        return new JdbcRecords(null, Collections.emptyIterator(), Collections.emptySet());
    }

    @Override
    public String nextSplit() {
        String nextSplit = splitId;
        splitId = null;
        currentRecords = nextSplit == null ? null : recordsForSplit;
        return nextSplit;
    }

    @Override
    public JdbcRecord nextRecordFromSplit() {
        if (currentRecords == null) {
            throw new IllegalStateException("No current JDBC split");
        }
        return currentRecords.hasNext() ? currentRecords.next() : null;
    }

    @Override
    public Set<String> finishedSplits() {
        return finishedSplits;
    }
}
