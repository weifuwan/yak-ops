package org.apache.flink.cdc.connectors.jdbc.source;

import org.apache.flink.api.connector.source.SplitEnumerator;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;
import org.apache.flink.api.connector.source.SourceEvent;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

/** Plans bounded table splits and redistributes pending splits after reader recovery. */
public final class JdbcSplitEnumerator
        implements SplitEnumerator<JdbcSourceSplit, JdbcEnumeratorState> {

    private final SplitEnumeratorContext<JdbcSourceSplit> context;
    private final JdbcSourceConfig config;
    private final JdbcEnumeratorState restoredState;
    private final Queue<JdbcSourceSplit> pending = new ArrayDeque<>();
    private boolean started;

    public JdbcSplitEnumerator(
            SplitEnumeratorContext<JdbcSourceSplit> context,
            JdbcSourceConfig config,
            JdbcEnumeratorState restoredState) {
        this.context = context;
        this.config = config;
        this.restoredState = restoredState;
    }

    @Override
    public void start() {
        if (!started) {
            started = true;
            if (restoredState == null) {
                try {
                    pending.addAll(new JdbcCatalog(config).discoverSplits());
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to enumerate JDBC source splits", e);
                }
            } else {
                pending.addAll(restoredState.getPendingSplits());
            }
        }
        assignPending();
    }

    private void assignPending() {
        List<Integer> readers = new ArrayList<>(context.registeredReaders().keySet());
        Collections.sort(readers);
        if (readers.isEmpty()) return;
        int index = 0;
        while (!pending.isEmpty()) {
            int reader = readers.get(index++ % readers.size());
            context.assignSplit(pending.remove(), reader);
        }
        for (Integer reader : readers) context.signalNoMoreSplits(reader);
    }

    @Override
    public void handleSplitRequest(int subtaskId, String requesterHostname) {}

    @Override
    public void addSplitsBack(List<JdbcSourceSplit> splits, int subtaskId) {
        pending.addAll(splits);
        assignPending();
    }

    @Override
    public void addReader(int subtaskId) {
        if (started) assignPending();
    }

    @Override
    public void handleSourceEvent(int subtaskId, SourceEvent sourceEvent) {}

    @Override
    public JdbcEnumeratorState snapshotState(long checkpointId) {
        return new JdbcEnumeratorState(new ArrayList<>(pending));
    }

    @Override
    public void close() throws IOException {}
}
