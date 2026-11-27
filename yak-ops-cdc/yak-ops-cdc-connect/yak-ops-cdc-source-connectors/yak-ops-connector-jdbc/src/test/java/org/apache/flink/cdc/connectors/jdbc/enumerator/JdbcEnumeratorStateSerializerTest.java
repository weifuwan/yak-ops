package org.apache.flink.cdc.connectors.jdbc.enumerator;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplit;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcEnumeratorStateSerializerTest {

    private final JdbcEnumeratorStateSerializer serializer = new JdbcEnumeratorStateSerializer();

    @Test
    void roundTripsVersionTwoEnumeratorState() throws Exception {
        JdbcSourceSplit split = createSplit();
        JdbcEnumeratorState state =
                new JdbcEnumeratorState(
                        Collections.singletonList(split),
                        Collections.singletonMap(2, Collections.singletonList(split)),
                        true);

        JdbcEnumeratorState restored =
                serializer.deserialize(serializer.getVersion(), serializer.serialize(state));

        assertTrue(restored.isEnumerationCompleted());
        assertEquals("orders-0", restored.getPendingSplits().get(0).splitId());
        assertEquals(
                "orders-0",
                restored.getPendingSplitsByReader().get(2).get(0).splitId());
    }

    @Test
    void readsLegacyJavaSerializedEnumeratorState() throws Exception {
        org.apache.flink.cdc.connectors.jdbc.source.JdbcSourceSplit legacySplit =
                new org.apache.flink.cdc.connectors.jdbc.source.JdbcSourceSplit(
                        "orders-0",
                        TableId.tableId("inventory", "orders"),
                        schema(),
                        "id",
                        BigDecimal.ONE,
                        BigDecimal.valueOf(100),
                        true,
                        BigDecimal.TEN);
        org.apache.flink.cdc.connectors.jdbc.source.JdbcEnumeratorState legacyState =
                new org.apache.flink.cdc.connectors.jdbc.source.JdbcEnumeratorState(
                        Collections.singletonList(legacySplit),
                        Collections.singletonMap(2, Collections.singletonList(legacySplit)),
                        true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(legacyState);
        }

        JdbcEnumeratorState restored = serializer.deserialize(1, bytes.toByteArray());

        assertTrue(restored.isEnumerationCompleted());
        assertEquals("orders-0", restored.getPendingSplits().get(0).splitId());
        assertEquals(
                BigDecimal.TEN,
                restored.getPendingSplitsByReader().get(2).get(0).getLastReadValue());
    }

    private static JdbcSourceSplit createSplit() {
        return new JdbcSourceSplit(
                "orders-0",
                TableId.tableId("inventory", "orders"),
                schema(),
                "id",
                BigDecimal.ONE,
                BigDecimal.valueOf(100),
                true,
                BigDecimal.TEN);
    }

    private static Schema schema() {
        return Schema.newBuilder()
                .physicalColumn("id", DataTypes.BIGINT())
                .primaryKey(Collections.singletonList("id"))
                .build();
    }
}
