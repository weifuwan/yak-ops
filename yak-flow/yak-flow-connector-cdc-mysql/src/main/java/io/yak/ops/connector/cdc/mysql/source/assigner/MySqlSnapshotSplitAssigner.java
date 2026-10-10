package io.yak.ops.connector.cdc.mysql.source.assigner;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tracks planned, reader-assigned and mailbox-completed JDBC snapshot ranges.
 *
 * <p>The Runtime still owns delivery ACK and Reader split cursors. This assigner keeps
 * only coordinator scheduling metadata and never treats split assignment as completion.
 */
public final class MySqlSnapshotSplitAssigner {

    private final Deque<MySqlSnapshotSplit> pending = new ArrayDeque<>();
    private final Map<String, MySqlSnapshotSplit> assigned = new LinkedHashMap<>();
    private final Set<String> finished = new LinkedHashSet<>();
    private int total;
    private boolean planned;

    public MySqlSnapshotSplitAssigner() {}

    public MySqlSnapshotSplitAssigner(
            List<MySqlSnapshotSplit> pending,
            Map<String, MySqlSnapshotSplit> assigned,
            Set<String> finished,
            int total,
            boolean planned) {
        this.pending.addAll(Objects.requireNonNull(pending, "pending"));
        this.assigned.putAll(Objects.requireNonNull(assigned, "assigned"));
        this.finished.addAll(Objects.requireNonNull(finished, "finished"));
        this.total = total;
        this.planned = planned;
        if (total < 0 || this.pending.size() + this.assigned.size() + this.finished.size() != total) {
            throw new IllegalArgumentException("Invalid restored snapshot assignment accounting");
        }
    }

    /** Registers an immutable complete plan after asynchronous table discovery finishes. */
    public void plan(List<MySqlSnapshotSplit> ranges) {
        if (planned) {
            throw new IllegalStateException("MySQL snapshot ranges were already planned");
        }
        for (MySqlSnapshotSplit split : ranges) {
            if (pending.stream().anyMatch(value -> value.splitId().equals(split.splitId()))) {
                throw new IllegalArgumentException("Duplicate MySQL snapshot split ID");
            }
            pending.addLast(split);
        }
        total = pending.size();
        planned = true;
    }

    /** Assigns a split without acknowledging its downstream consumption. */
    public MySqlSnapshotSplit next() {
        MySqlSnapshotSplit split = pending.pollFirst();
        if (split != null) {
            assigned.put(split.splitId(), split);
        }
        return split;
    }

    public void complete(String splitId) {
        if (finished.contains(splitId)) {
            return;
        }
        if (assigned.remove(splitId) == null) {
            throw new IllegalArgumentException("Completed MySQL snapshot split was not assigned");
        }
        finished.add(splitId);
    }

    public void addBack(MySqlSnapshotSplit split) {
        if (finished.contains(split.splitId()) || assigned.remove(split.splitId()) == null) {
            throw new IllegalArgumentException("Returned snapshot split is not assigned");
        }
        pending.addFirst(split);
    }

    public boolean allFinished() {
        return planned && pending.isEmpty() && assigned.isEmpty() && finished.size() == total;
    }

    public boolean planned() {
        return planned;
    }

    public int total() {
        return total;
    }

    public List<MySqlSnapshotSplit> pending() {
        return List.copyOf(new ArrayList<>(pending));
    }

    public Map<String, MySqlSnapshotSplit> assigned() {
        return Map.copyOf(assigned);
    }

    public Set<String> finished() {
        return Set.copyOf(finished);
    }
}
