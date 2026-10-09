package io.yak.ops.core.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LogicalTypesTest {

    @Test
    void primitiveAndResolvedDecimalTypesAreStableValues() {
        assertEquals(LogicalTypeRoot.INTEGER, LogicalTypes.INTEGER.getTypeRoot());
        assertEquals("INT", LogicalTypes.INTEGER.asSerializableString());
        assertEquals(new DecimalType(18, 2), LogicalTypes.decimal(18, 2));
        assertEquals(LogicalTypeRoot.DECIMAL, LogicalTypes.decimal(10, 0).getTypeRoot());
        assertEquals("DECIMAL(18, 2)", LogicalTypes.decimal(18, 2).asSerializableString());
        assertEquals(BigDecimal.class, LogicalTypes.decimal(18, 2).getDefaultConversion());
    }

    @Test
    void nullabilityIsOwnedByLogicalTypeAndParticipatesInEquality() {
        LogicalType required = LogicalTypes.varchar(64).copy(false);
        assertFalse(required.isNullable());
        assertEquals("VARCHAR(64) NOT NULL", required.asSerializableString());
        assertEquals(new VarCharType(false, 64), required);
        assertEquals(new VarCharType(true, 64), required.copy(true));
        assertFalse(required.equals(LogicalTypes.varchar(64)));
        assertEquals("STRING", LogicalTypes.STRING.asSerializableString());
        assertEquals("BYTES", LogicalTypes.BINARY.asSerializableString());
    }

    @Test
    void unknownAndOutOfRangeCatalogDecimalStayUnresolved() {
        LogicalType unknown = LogicalTypes.decimal((Integer) null, (Integer) null);
        UnresolvedDecimalType metadata = assertInstanceOf(UnresolvedDecimalType.class, unknown);
        assertNull(metadata.precision());
        assertEquals("UNRESOLVED_DECIMAL(?, ?)", metadata.asSerializableString());
        assertFalse(metadata.isResolved());

        LogicalType huge = LogicalTypes.decimal(Integer.valueOf(65), Integer.valueOf(30));
        assertInstanceOf(UnresolvedDecimalType.class, huge);
        assertEquals(65, LogicalTypes.decimalPrecision(huge));
        assertEquals(30, LogicalTypes.decimalScale(huge));
        assertTrue(LogicalTypes.decimal(38, 20).isResolved());
    }

    @Test
    void invalidResolvedDecimalPrecisionAndScaleAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BasicType(LogicalTypeRoot.DECIMAL));
        assertThrows(IllegalArgumentException.class, () -> LogicalTypes.decimal(0, 2));
        assertThrows(IllegalArgumentException.class, () -> LogicalTypes.decimal(39, 2));
        assertThrows(IllegalArgumentException.class, () -> LogicalTypes.decimal(3, 4));
        assertThrows(IllegalArgumentException.class, () -> LogicalTypes.decimal(10, -1));
        assertInstanceOf(UnresolvedDecimalType.class, LogicalTypes.decimal(Integer.valueOf(4), null));
    }

    @Test
    void temporalTypesRetainPrecisionAndCorrectRoots() {
        assertEquals("TIME(3)", LogicalTypes.time(3).asSerializableString());
        assertEquals("TIMESTAMP(9)", LogicalTypes.timestamp(9).asSerializableString());
        assertEquals("TIMESTAMP(6) WITH TIME ZONE", LogicalTypes.zonedTimestamp(6).asSerializableString());
        assertEquals(LogicalTypeRoot.TIMESTAMP_WITH_TIME_ZONE, LogicalTypes.zonedTimestamp(6).getTypeRoot());
        assertThrows(IllegalArgumentException.class, () -> LogicalTypes.time(10));
        assertThrows(IllegalArgumentException.class, () -> LogicalTypes.timestamp(-1));
        assertThrows(IllegalArgumentException.class, () -> LogicalTypes.zonedTimestamp(10));
    }

    @Test
    void capacityIsOwnedByParameterizedTypeInsteadOfColumnMetadata() {
        Column name = new Column("name", LogicalTypes.STRING, false, 120);
        assertEquals(120, name.length());
        assertFalse(name.nullable());
        assertEquals(new VarCharType(false, 120), name.dataType());
        assertEquals("VARCHAR(120) NOT NULL", name.dataType().asSerializableString());
        assertEquals(30, new Column("payload", LogicalTypes.fixedBinary(30)).length());
        assertNull(new Column("unknown", LogicalTypes.STRING).length());
        assertThrows(IllegalArgumentException.class, () -> new Column("id", LogicalTypes.INTEGER, false, 10));
        assertThrows(IllegalArgumentException.class, () -> LogicalTypes.varchar(0));
        assertThrows(IllegalArgumentException.class, () -> LogicalTypes.fixedBinary(0));
    }
}
