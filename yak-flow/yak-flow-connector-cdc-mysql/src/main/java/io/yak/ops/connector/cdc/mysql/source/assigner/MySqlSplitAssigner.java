package io.yak.ops.connector.cdc.mysql.source.assigner;

import io.yak.ops.connector.cdc.mysql.source.enumerator.PendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import java.util.List;

/**
 * Owns MySQL split assignment and the coordinator portion of checkpoint state.
 *
 * <p>Reader offsets and Schema History remain Reader-owned; successful checkpoint
 * completion is separate from split delivery and snapshot completion.
 */
public interface MySqlSplitAssigner<SplitT extends MySqlSplit, StateT extends PendingSplitsState> {

    /** Returns the next split for a reader, or null when no split can be assigned. */
    SplitT getNext(int subtaskId);

    /** Returns reader-owned splits after an unsuccessful execution attempt. */
    void addSplitsBack(List<SplitT> splits, int subtaskId);

    /** Saves only assignment and discovery state, not Reader progress. */
    StateT snapshotState(long checkpointId);

    /** Returns true only when a completed checkpoint enables the streaming handoff. */
    boolean notifyCheckpointComplete(long checkpointId);
}
