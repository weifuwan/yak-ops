package org.apache.flink.cdc.connectors.jdbc.enumerator;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.api.connector.source.SplitEnumerator;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.connectors.jdbc.catalog.JdbcCatalog;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSourceConfig;
import org.apache.flink.cdc.connectors.jdbc.connection.JdbcConnectionFactory;
import org.apache.flink.cdc.connectors.jdbc.dialect.JdbcDialect;
import org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplit;
import org.apache.flink.cdc.connectors.jdbc.split.JdbcSplitPlanner;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

/** Discovers bounded table splits and assigns them to registered source readers. */
public final class JdbcSplitEnumerator
        implements SplitEnumerator<JdbcSourceSplit, JdbcEnumeratorState> {

    private final SplitEnumeratorContext<JdbcSourceSplit> context;
    private final JdbcSourceConfig config;
    private final JdbcEnumeratorState restoredState;
    private final Queue<JdbcSourceSplit> pending = new ArrayDeque<>();
    private final java.util.Map<Integer, Queue<JdbcSourceSplit>> pendingByReader =
            new java.util.HashMap<>();

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
        if (started) {
            return;
        }
        started = true;
        if (restoredState != null) {
            pending.addAll(restoredState.getPendingSplits());
            restoredState.getPendingSplitsByReader().forEach(
                    (reader, splits) ->
                            pendingByReader.put(reader, new ArrayDeque<>(splits)));
            enumerationCompleted = restoredState.isEnumerationCompleted();
        }
        if (!enumerationCompleted) {
            enumerateAsync();
        } else {
            assignPending();
        }
    }

    private void enumerateAsync() {
        if (enumerationInProgress) {
            return;
        }
        enumerationInProgress = true;
        context.callAsync(
                this::discoverSplits,
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

    private List<JdbcSourceSplit> discoverSplits() throws SQLException {
        List<JdbcSourceSplit> splits = new ArrayList<>();
        try (Connection connection = JdbcConnectionFactory.openConnection(config)) {
            JdbcDialect dialect = JdbcDialect.create(connection.getMetaData());
            JdbcCatalog catalog = new JdbcCatalog(config);
            for (String tableName : config.getTables()) {
                TableId tableId = TableId.parse(tableName);
                Schema schema = catalog.getTableSchema(connection, dialect, tableId);
                splits.addAll(
                        JdbcSplitPlanner.plan(
                                connection,
                                dialect,
                                tableId,
                                schema,
                                config.getSplitSize(),
                                config.getParallelism()));
            }
        }
        return splits;
    }

    private void assignPending() {
        if (!enumerationCompleted) {
            return;
        }
        List<Integer> readers = new ArrayList<>(context.registeredReaders().keySet());
        Collections.sort(readers);
        if (readers.size() < context.currentParallelism()) {
            return;
        }

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
        pendingByReader
                .computeIfAbsent(subtaskId, ignored -> new ArrayDeque<>())
                .addAll(splits);
        if (context.registeredReaders().containsKey(subtaskId)) {
            assignReturnedSplits(subtaskId);
        }
        if (started) {
            assignPending();
        }
    }

    @Override
    public void addReader(int subtaskId) {
        assignReturnedSplits(subtaskId);
        if (started) {
            assignPending();
        }
    }

    private void assignReturnedSplits(int subtaskId) {
        Queue<JdbcSourceSplit> returned = pendingByReader.remove(subtaskId);
        if (returned == null || returned.isEmpty()) {
            return;
        }
        while (!returned.isEmpty()) {
            context.assignSplit(returned.remove(), subtaskId);
        }
    }

    @Override
    public void handleSourceEvent(int subtaskId, SourceEvent sourceEvent) {}

    @Override
    public JdbcEnumeratorState snapshotState(long checkpointId) {
        java.util.Map<Integer, List<JdbcSourceSplit>> returned = new java.util.HashMap<>();
        pendingByReader.forEach(
                (reader, splits) -> returned.put(reader, new ArrayList<>(splits)));
        return new JdbcEnumeratorState(
                new ArrayList<>(pending), returned, enumerationCompleted);
    }

    @Override
    public void close() throws IOException {}
}
