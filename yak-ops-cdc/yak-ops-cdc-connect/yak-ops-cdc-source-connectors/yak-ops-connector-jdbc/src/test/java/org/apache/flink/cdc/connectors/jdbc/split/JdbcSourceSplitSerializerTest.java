package org.apache.flink.cdc.connectors.jdbc.split;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataTypes;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcSourceSplitSerializerTest {

    private final JdbcSourceSplitSerializer serializer = new JdbcSourceSplitSerializer();

    @Test
    void roundTripsVersionTwoSplit() throws Exception {
        JdbcSourceSplit split = createSplit();

        JdbcSourceSplit restored =
                serializer.deserialize(serializer.getVersion(), serializer.serialize(split));

        assertEquals(split.splitId(), restored.splitId());
        assertEquals(split.getTableId(), restored.getTableId());
        assertEquals(split.getSchema(), restored.getSchema());
        assertEquals(split.getSplitColumn(), restored.getSplitColumn());
        assertEquals(split.getLowerBound(), restored.getLowerBound());
        assertEquals(split.getUpperBound(), restored.getUpperBound());
        assertEquals(split.isUpperInclusive(), restored.isUpperInclusive());
        assertEquals(split.getLastReadValue(), restored.getLastReadValue());
    }

    @Test
    void readsLegacyJavaSerializedSplit() throws Exception {
        org.apache.flink.cdc.connectors.jdbc.source.JdbcSourceSplit legacy =
                new org.apache.flink.cdc.connectors.jdbc.source.JdbcSourceSplit(
                        "orders-0",
                        TableId.tableId("inventory", "orders"),
                        schema(),
                        "id",
                        BigDecimal.ONE,
                        BigDecimal.valueOf(100),
                        true,
                        BigDecimal.TEN);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(legacy);
        }

        JdbcSourceSplit restored = serializer.deserialize(1, bytes.toByteArray());

        assertEquals(legacy.splitId(), restored.splitId());
        assertEquals(legacy.getTableId(), restored.getTableId());
        assertEquals(legacy.getLastReadValue(), restored.getLastReadValue());
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
