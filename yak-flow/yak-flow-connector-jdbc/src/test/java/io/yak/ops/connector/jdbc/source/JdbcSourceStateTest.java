package io.yak.ops.connector.jdbc.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcEnumeratorState;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcEnumeratorStateSerializer;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplitSerializer;
import io.yak.ops.core.data.TableId;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies durable split and enumerator state encoding independently of a database. */
class JdbcSourceStateTest {

    private final TableId table = new TableId("store", "public", "orders");
    private final JdbcSourceSplitSerializer splitSerializer = new JdbcSourceSplitSerializer();

    @Test
    void splitRoundTripRetainsTableIdentityBoundsColumnsAndProgress() throws Exception {
        JdbcSourceSplit before = new JdbcSourceSplit(
                "table-0-split-2", table, List.of("id", "name"), "id", 10L, 20L, 13L, "a".repeat(64));
        JdbcSourceSplit restored =
                splitSerializer.deserialize(splitSerializer.getVersion(), splitSerializer.serialize(before));
        assertEquals(before, restored);
        assertThrows(IOException.class, () -> splitSerializer.deserialize(99, splitSerializer.serialize(before)));
        assertThrows(IOException.class, () -> splitSerializer.deserialize(1, splitSerializer.serialize(before)));
    }

    @Test
    void incrementalSplitRoundTripPinsCursorAndCheckpointProgress() throws Exception {
        JdbcIncrementalRange range = new JdbcIncrementalRange(
                JdbcIncrementalRange.Mode.TIMESTAMP, "updated_at", "2026-10-01T00:00:00", "2026-10-02T00:00:00");
        JdbcSourceSplit split = new JdbcSourceSplit(
                "incremental", table, List.of("id", "updated_at"), "id", 1L, 100L, 10L, "c".repeat(64), range);
        JdbcSourceSplit restored = splitSerializer.deserialize(splitSerializer.getVersion(), splitSerializer.serialize(split));
        assertEquals(split, restored);
        assertEquals(range, restored.withLastEmittedKey(11L).incrementalRange());
        assertThrows(IllegalArgumentException.class,
                () -> new JdbcIncrementalRange(JdbcIncrementalRange.Mode.TIMESTAMP, "updated_at",
                        "2026-10-03T00:00:00", "2026-10-02T00:00:00"));
    }

    @Test
    void readIntentAndItsHiddenPredicateColumnsSurviveCheckpointRoundTrip() throws Exception {
        JdbcReadOptions read = new JdbcReadOptions("enabled = 1 OR id = 7", "sequence_id", 25, 4);
        JdbcSourceSplit split = new JdbcSourceSplit("filtered", table, List.of("name"), "sequence_id",
                10L, 99L, 12L, "d".repeat(64), null, read);
        JdbcSourceSplit restored = splitSerializer.deserialize(splitSerializer.getVersion(), splitSerializer.serialize(split));
        assertEquals(split, restored);
        assertEquals(List.of("name", "sequence_id", "enabled", "id"), restored.readColumns());
        assertEquals(read, restored.withLastEmittedKey(13L).readOptions());
        assertThrows(IllegalArgumentException.class, () -> new JdbcReadOptions("1 = 1", "", 25, 4));
    }

    @Test
    void historicalSplitAndEnumeratorFormatsRetainTheirOriginalReadSemantics() throws Exception {
        String oldSplit = "00066c656761637901000573746f72650100067075626c696300066f7264657273000000020002696400046e616d65"
                + "010002696401000000000000000101000000000000000a0100000000000000030040" + "61".repeat(64);
        for (int version : List.of(2, 3)) {
            byte[] split = HexFormat.of().parseHex(oldSplit + (version == 3
                    ? "01000a494e4352454153494e47000269640100013000023130" : ""));
            JdbcIncrementalRange range = version == 3
                    ? new JdbcIncrementalRange(JdbcIncrementalRange.Mode.INCREASING, "id", "0", "10") : null;
            JdbcSourceSplit expected = new JdbcSourceSplit("legacy", table, List.of("id", "name"),
                    "id", 1L, 10L, 3L, "a".repeat(64), range);
            assertEquals(expected, splitSerializer.deserialize(version, split));
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF("legacy-definition");
                out.writeInt(1);
                out.writeInt(1);
                out.writeInt(split.length);
                out.write(split);
                out.flush();
                assertEquals(new JdbcEnumeratorState("legacy-definition", 1, List.of(expected)),
                        new JdbcEnumeratorStateSerializer().deserialize(version, bytes.toByteArray()));
            }
        }
    }

    @Test
    void stateRoundTripAndDefinitionMismatchFailClosed() throws Exception {
        JdbcSource source = new JdbcSource(
                new JdbcConnectionOptions("jdbc:h2:mem:snapshot", "sa", ""), List.of(table));
        JdbcEnumeratorState state = new JdbcEnumeratorState(
                "fingerprint", 1, List.of(new JdbcSourceSplit("s", table, List.of("id"), null, null, null, null, "b".repeat(64))));
        JdbcEnumeratorStateSerializer serializer = new JdbcEnumeratorStateSerializer();
        assertEquals(state, serializer.deserialize(serializer.getVersion(), serializer.serialize(state)));
        assertThrows(IOException.class, () -> serializer.deserialize(1, serializer.serialize(state)));
        assertThrows(IllegalArgumentException.class, () -> source.restoreEnumerator(null, state));
    }

    @Test
    void invalidStateAndUnsignedLongRangesAreNotSilentlyAccepted() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcSourceSplit("broken", table, List.of("id"), "id", 5L, 2L, null, "a".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcSourceSplit("broken", table, List.of("id"), null, null, null, 1L, "a".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcSource(new JdbcConnectionOptions("jdbc:h2:mem:snapshot", "sa", ""), List.of(table, table)));
    }
}
