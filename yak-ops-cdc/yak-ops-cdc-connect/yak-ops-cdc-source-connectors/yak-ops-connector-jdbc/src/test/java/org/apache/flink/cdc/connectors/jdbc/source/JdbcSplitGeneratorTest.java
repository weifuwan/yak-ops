package org.apache.flink.cdc.connectors.jdbc.source;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataTypes;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcSplitGeneratorTest {

    @Test
    void createsAdjacentRangesCoveringTheNumericKeyDomain() {
        TableId tableId = TableId.tableId("orders_db", "orders");
        Schema schema =
                Schema.newBuilder()
                        .physicalColumn("id", DataTypes.BIGINT())
                        .primaryKey(Collections.singletonList("id"))
                        .build();

        List<JdbcSourceSplit> splits =
                JdbcSplitGenerator.createRangeSplits(
                        tableId,
                        schema,
                        "id",
                        BigDecimal.ONE,
                        BigDecimal.valueOf(100),
                        100,
                        10,
                        2);

        assertEquals(8, splits.size());
        assertEquals(BigDecimal.ONE, splits.get(0).getLowerBound());
        assertEquals(BigDecimal.valueOf(100), splits.get(splits.size() - 1).getUpperBound());
        assertTrue(splits.get(splits.size() - 1).isUpperInclusive());
        for (int index = 1; index < splits.size(); index++) {
            assertEquals(
                    splits.get(index - 1).getUpperBound(),
                    splits.get(index).getLowerBound());
            assertFalse(splits.get(index - 1).isUpperInclusive());
        }
        assertEquals(
                splits.size(),
                splits.stream().map(JdbcSourceSplit::splitId).collect(Collectors.toSet()).size());
    }

    @Test
    void emptyAndSingleValueTablesProduceOneSafeSplit() {
        TableId tableId = TableId.tableId("orders_db", "orders");
        Schema schema =
                Schema.newBuilder()
                        .physicalColumn("id", DataTypes.BIGINT())
                        .primaryKey(Collections.singletonList("id"))
                        .build();

        List<JdbcSourceSplit> empty =
                JdbcCatalog.createRangeSplits(
                        tableId, schema, "id", null, null, 0, 1000, 4);
        List<JdbcSourceSplit> single =
                JdbcCatalog.createRangeSplits(
                        tableId,
                        schema,
                        "id",
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        1,
                        1000,
                        4);

        assertEquals(1, empty.size());
        assertEquals(1, single.size());
        assertTrue(single.get(0).isUpperInclusive());
    }
}
