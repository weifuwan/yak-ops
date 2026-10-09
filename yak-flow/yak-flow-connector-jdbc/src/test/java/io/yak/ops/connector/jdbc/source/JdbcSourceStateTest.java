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
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies durable split and enumerator state encoding independently of a database. */
class JdbcSourceStateTest {

    private final TableId table = new TableId("store", "public", "orders");
    private final JdbcSourceSplitSerializer splitSerializer = new JdbcSourceSplitSerializer();

    @Test
    void splitRoundTripRetainsTableIdentityBoundsColumnsAndProgress() throws Exception {
        JdbcSourceSplit before = new JdbcSourceSplit(
                "table-0-split-2", table, List.of("id", "name"), "id", 10L, 20L, 13L);
        JdbcSourceSplit restored =
                splitSerializer.deserialize(splitSerializer.getVersion(), splitSerializer.serialize(before));
        assertEquals(before, restored);
        assertThrows(IOException.class, () -> splitSerializer.deserialize(99, splitSerializer.serialize(before)));
    }

    @Test
    void stateRoundTripAndDefinitionMismatchFailClosed() throws Exception {
        JdbcSource source = new JdbcSource(
                new JdbcConnectionOptions("jdbc:h2:mem:snapshot", "sa", ""), List.of(table));
        JdbcEnumeratorState state = new JdbcEnumeratorState(
                "fingerprint", 1, List.of(new JdbcSourceSplit("s", table, List.of("id"), null, null, null, null)));
        JdbcEnumeratorStateSerializer serializer = new JdbcEnumeratorStateSerializer();
        assertEquals(state, serializer.deserialize(serializer.getVersion(), serializer.serialize(state)));
        assertThrows(IllegalArgumentException.class, () -> source.restoreEnumerator(null, state));
    }

    @Test
    void invalidStateAndUnsignedLongRangesAreNotSilentlyAccepted() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcSourceSplit("broken", table, List.of("id"), "id", 5L, 2L, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcSourceSplit("broken", table, List.of("id"), null, null, null, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcSource(new JdbcConnectionOptions("jdbc:h2:mem:snapshot", "sa", ""), List.of(table, table)));
    }
}
