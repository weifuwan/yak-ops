package io.yak.ops.connector.base.source.reader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable, insertion-ordered fetch batch that may also carry completed split IDs. */
public final class RecordsBySplits<E> implements RecordsWithSplitIds<E> {

    private final Set<String> finishedSplits;
    private final Iterator<Map.Entry<String, List<E>>> splits;
    private Iterator<E> currentRecords;

    public RecordsBySplits(Map<String, ? extends Collection<E>> records, Set<String> finishedSplits) {
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(finishedSplits, "finishedSplits");
        Map<String, List<E>> ordered = new LinkedHashMap<>();
        records.forEach((id, values) -> {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Split ID must not be blank");
            }
            ordered.put(id, new ArrayList<>(Objects.requireNonNull(values, "split records")));
        });
        this.splits = ordered.entrySet().iterator();
        this.finishedSplits = Set.copyOf(new LinkedHashSet<>(finishedSplits));
    }

    @Override
    public String nextSplit() {
        if (!splits.hasNext()) {
            currentRecords = null;
            return null;
        }
        Map.Entry<String, List<E>> next = splits.next();
        currentRecords = next.getValue().iterator();
        return next.getKey();
    }

    @Override
    public E nextRecordFromSplit() {
        if (currentRecords == null) {
            throw new IllegalStateException("Call nextSplit before consuming a record");
        }
        return currentRecords.hasNext() ? currentRecords.next() : null;
    }

    @Override
    public Set<String> finishedSplits() {
        return finishedSplits;
    }
}
