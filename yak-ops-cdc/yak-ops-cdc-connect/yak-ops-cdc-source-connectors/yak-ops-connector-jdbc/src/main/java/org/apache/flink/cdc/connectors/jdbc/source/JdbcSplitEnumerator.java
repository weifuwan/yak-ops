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

/** Plans bounded table splits asynchronously and redistributes pending splits after recovery. */
public final class JdbcSplitEnumerator
        implements SplitEnumerator<JdbcSourceSplit, JdbcEnumeratorState> {

    private final SplitEnumeratorContext<JdbcSourceSplit> context;
    private final JdbcSourceConfig config;
    private final JdbcEnumeratorState restoredState;
    private final Queue<JdbcSourceSplit> pending = new ArrayDeque<>();

    private boolean started;
    private boolean enumerationCompleted;
    private boolean enumerationInProgress;

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
        if (started) return;
        started = true;
        if (restoredState != null) {
            pending.addAll(restoredState.getPendingSplits());
            enumerationCompleted = restoredState.isEnumerationCompleted();
        }
        if (!enumerationCompleted) {
            enumerateAsync();
        } else {
            assignPending();
        }
    }

    private void enumerateAsync() {
        if (enumerationInProgress) return;
        enumerationInProgress = true;
        context.callAsync(
                () -> new JdbcCatalog(config).discoverSplits(),
                (splits, failure) -> {
                    enumerationInProgress = false;
                    if (failure != null) {
                        throw new IllegalStateException(
                                "Failed to enumerate JDBC source splits", failure);
                    }
                    pending.addAll(splits);
                    enumerationCompleted = true;
                    assignPending();
                });
    }

    private void assignPending() {
        if (!enumerationCompleted) return;
        List<Integer> readers = new ArrayList<>(context.registeredReaders().keySet());
        Collections.sort(readers);
        if (readers.size() < context.currentParallelism()) return;

        int index = 0;
        while (!pending.isEmpty()) {
            int reader = readers.get(index++ % readers.size());
            context.assignSplit(pending.remove(), reader);
        }
        for (Integer reader : readers) {
            context.signalNoMoreSplits(reader);
        }
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
        return new JdbcEnumeratorState(new ArrayList<>(pending), enumerationCompleted);
    }

    @Override
    public void close() throws IOException {}
}
