package io.yak.ops.connector.jdbc.source.enumerator;

import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.data.TableId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Discovers one table at a time and assigns its splits to requesting readers.
 *
 * <p>Expensive metadata/statistics queries run through the coordinator's asynchronous discovery
 * facility. A completed checkpoint records the next unplanned table and the unassigned splits;
 * splits already owned by readers remain in their reader/coordinator checkpoint state.
 */
public final class JdbcSourceEnumerator implements SplitEnumerator<JdbcSourceSplit, JdbcEnumeratorState> {

    private final SplitEnumeratorContext<JdbcSourceSplit> context;
    private final JdbcSplitPlanner planner;
    private final List<TableId> tables;
    private final String fingerprint;
    private final Deque<JdbcSourceSplit> pending;
    private final LinkedHashSet<Integer> waitingReaders = new LinkedHashSet<>();
    private final Set<Integer> finishedReaders = new HashSet<>();

    private int nextTableIndex;
    private boolean planning;
    private boolean started;
    private boolean closed;

    public JdbcSourceEnumerator(
            SplitEnumeratorContext<JdbcSourceSplit> context,
            JdbcSplitPlanner planner,
            List<TableId> tables,
            String fingerprint,
            JdbcEnumeratorState restored) {
        this.context = Objects.requireNonNull(context, "context");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.tables = List.copyOf(Objects.requireNonNull(tables, "tables"));
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        if (restored != null) {
            if (!fingerprint.equals(restored.sourceFingerprint()) || restored.nextTableIndex() > tables.size()) {
                throw new IllegalArgumentException("The JDBC source definition changed since its checkpoint");
            }
            nextTableIndex = restored.nextTableIndex();
            pending = new ArrayDeque<>(restored.pendingSplits());
        } else {
            pending = new ArrayDeque<>();
        }
    }

    @Override
    public void start() {
        if (started || closed) {
            throw new IllegalStateException("JDBC enumerator cannot be started again");
        }
        started = true;
        discoverNext();
        drainRequests();
    }

    @Override
    public void handleSplitRequest(int subtaskId) {
        if (closed || finishedReaders.contains(subtaskId)) {
            return;
        }
        if (!context.registeredReaders().contains(subtaskId)) {
            throw new IllegalArgumentException("JDBC reader is not registered: " + subtaskId);
        }
        waitingReaders.add(subtaskId);
        drainRequests();
    }

    @Override
    public void addReader(int subtaskId) {
        if (closed) {
            return;
        }
        signalCompletion();
    }

    @Override
    public void addSplitsBack(List<JdbcSourceSplit> splits, int subtaskId) {
        if (closed) {
            throw new IllegalStateException("JDBC enumerator is closed");
        }
        if (!finishedReaders.isEmpty()) {
            throw new IllegalStateException("Reader-local split reassignment after no-more-splits is unsupported");
        }
        List<JdbcSourceSplit> returned = List.copyOf(splits);
        for (JdbcSourceSplit split : returned) {
            pending.addLast(split);
        }
        if (context.registeredReaders().contains(subtaskId)) {
            waitingReaders.add(subtaskId);
        }
        drainRequests();
    }

    @Override
    public JdbcEnumeratorState snapshotState(long checkpointId) {
        if (checkpointId < 0 || closed) {
            throw new IllegalStateException("Invalid JDBC enumerator checkpoint");
        }
        return new JdbcEnumeratorState(fingerprint, nextTableIndex, new ArrayList<>(pending));
    }

    @Override
    public void close() {
        closed = true;
        pending.clear();
        waitingReaders.clear();
    }

    private void discoverNext() {
        if (!started || closed || planning || nextTableIndex >= tables.size()) {
            signalCompletion();
            return;
        }
        int index = nextTableIndex;
        TableId table = tables.get(index);
        planning = true;
        context.callAsync(() -> planner.plan(table, index), (planned, failure) -> {
            planning = false;
            if (closed) {
                return;
            }
            if (failure != null) {
                throw new IllegalStateException("JDBC table split planning failed", failure);
            }
            if (index != nextTableIndex) {
                throw new IllegalStateException("Concurrent JDBC table planning is unsupported");
            }
            pending.addAll(Objects.requireNonNull(planned, "planned splits"));
            nextTableIndex++;
            drainRequests();
            discoverNext();
        });
    }

    private void drainRequests() {
        while (!pending.isEmpty() && !waitingReaders.isEmpty()) {
            int subtaskId = waitingReaders.iterator().next();
            if (!context.registeredReaders().contains(subtaskId) || finishedReaders.contains(subtaskId)) {
                waitingReaders.remove(subtaskId);
                continue;
            }
            // Do not lose pending work if the coordinator rejects the assignment.
            // Assignment acknowledgement and in-flight ownership belong to Runtime.
            context.assignSplit(pending.peekFirst(), subtaskId);
            pending.removeFirst();
            waitingReaders.remove(subtaskId);
        }
        signalCompletion();
    }

    private void signalCompletion() {
        if (!started || planning || nextTableIndex < tables.size() || !pending.isEmpty() || closed) {
            return;
        }
        for (int id : context.registeredReaders()) {
            if (finishedReaders.add(id)) {
                context.signalNoMoreSplits(id);
            }
        }
        waitingReaders.clear();
    }
}
