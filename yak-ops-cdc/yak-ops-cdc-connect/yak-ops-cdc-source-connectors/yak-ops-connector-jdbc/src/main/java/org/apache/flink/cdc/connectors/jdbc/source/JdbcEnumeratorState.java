package org.apache.flink.cdc.connectors.jdbc.source;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy version-1 checkpoint class.
 *
 * <p>Keep the class name, serialVersionUID and serialized fields stable for old Java-serialized
 * enumerator checkpoints. New execution uses the enumerator package.
 */
@Deprecated
public final class JdbcEnumeratorState implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<JdbcSourceSplit> pendingSplits;
    private final Map<Integer, List<JdbcSourceSplit>> pendingSplitsByReader;
    private final boolean enumerationCompleted;

    public JdbcEnumeratorState(
            List<JdbcSourceSplit> pendingSplits,
            Map<Integer, List<JdbcSourceSplit>> pendingSplitsByReader,
            boolean enumerationCompleted) {
        this.pendingSplits = Collections.unmodifiableList(new ArrayList<>(pendingSplits));
        Map<Integer, List<JdbcSourceSplit>> copy = new HashMap<>();
        pendingSplitsByReader.forEach(
                (reader, splits) ->
                        copy.put(reader, Collections.unmodifiableList(new ArrayList<>(splits))));
        this.pendingSplitsByReader = Collections.unmodifiableMap(copy);
        this.enumerationCompleted = enumerationCompleted;
    }

    public List<JdbcSourceSplit> getPendingSplits() {
        return pendingSplits;
    }

    public Map<Integer, List<JdbcSourceSplit>> getPendingSplitsByReader() {
        return pendingSplitsByReader;
    }

    public boolean isEnumerationCompleted() {
        return enumerationCompleted;
    }
}
