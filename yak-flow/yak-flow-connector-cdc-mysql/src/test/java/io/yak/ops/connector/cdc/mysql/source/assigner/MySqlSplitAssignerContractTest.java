package io.yak.ops.connector.cdc.mysql.source.assigner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlPendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.enumerator.PendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies assignment and recovery using the same contract for both startup modes. */
class MySqlSplitAssignerContractTest {

    @Test
    void binlogOnlyReturnsOneSplitAndPersistsAssignedOwnership() throws Exception {
        MySqlSplitAssigner<MySqlBinlogSplit, MySqlPendingSplitsState> assigner =
                new MySqlBinlogSplitAssigner("fp", null);
        assertNull(assigner.getNext(1));
        MySqlBinlogSplit split = assigner.getNext(0);
        assertEquals(MySqlBinlogSplit.ID, split.splitId());
        assertInstanceOf(MySqlSplit.class, split);
        assertNull(assigner.getNext(0));
        assertFalse(assigner.notifyCheckpointComplete(1));

        MySqlPendingSplitsState assigned = assigner.snapshotState(2);
        assertInstanceOf(PendingSplitsState.class, assigned);
        assertTrue(assigned.splitAssigned());
        var serializer = new MySqlPendingSplitsStateSerializer();
        var restored = new MySqlBinlogSplitAssigner(
                "fp", serializer.deserialize(serializer.getVersion(), serializer.serialize(assigned)));
        assertNull(restored.getNext(0));

        assigner.addSplitsBack(List.of(split), 0);
        assertFalse(assigner.snapshotState(3).splitAssigned());
        assertEquals(split, assigner.getNext(0));
        assertThrows(IllegalStateException.class, () -> assigner.addSplitsBack(List.of(split), 1));
        assertThrows(IllegalArgumentException.class, () -> new MySqlBinlogSplitAssigner("changed", assigned));
    }

    @Test
    void pendingBinlogSurvivesCoordinatorCheckpointWithoutMovingItsOffset() throws Exception {
        var assigner = new MySqlBinlogSplitAssigner("fp", null);
        MySqlPendingSplitsState pending = assigner.snapshotState(1);
        assertFalse(pending.splitAssigned());
        var serializer = new MySqlPendingSplitsStateSerializer();
        var restored = new MySqlBinlogSplitAssigner(
                "fp", serializer.deserialize(serializer.getVersion(), serializer.serialize(pending)));
        var split = restored.getNext(0);
        assertNull(split.offset());
        assertEquals(0, split.schemaHistory().length);
        assertThrows(IllegalArgumentException.class, () -> assigner.snapshotState(-1));
    }
}
