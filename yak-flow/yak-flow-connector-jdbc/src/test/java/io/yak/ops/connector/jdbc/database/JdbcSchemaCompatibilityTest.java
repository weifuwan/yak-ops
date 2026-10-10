package io.yak.ops.connector.jdbc.database;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
import org.junit.jupiter.api.Test;

class JdbcSchemaCompatibilityTest {

    @Test
    void shouldAllowIntegerWideningAndRejectNarrowing() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.INTEGER), column("target", LogicalTypes.BIGINT)));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.BIGINT), column("target", LogicalTypes.SMALLINT)));
    }

    @Test
    void shouldValidateIntegerToDecimalCapacity() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.INTEGER), column("target", LogicalTypes.decimal(12, 2))));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.BIGINT), column("target", LogicalTypes.decimal(18, 0))));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.BIGINT), column("target", LogicalTypes.decimal(null, null))));
    }

    @Test
    void shouldRejectDecimalPrecisionOrScaleLoss() {
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.decimal(10, 2)), column("target", LogicalTypes.decimal(9, 2))));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.decimal(10, 2)), column("target", LogicalTypes.decimal(12, 1))));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.decimal(10, 2)), column("target", LogicalTypes.decimal(12, 2))));
    }

    @Test
    void shouldPreserveUnknownDecimalCompatibility() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.decimal(null, null)),
                column("target", LogicalTypes.decimal(12, 2))));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.decimal(10, null)),
                column("target", LogicalTypes.decimal(12, null))));
    }

    @Test
    void shouldRejectSmallerStringAndBinaryCapacity() {
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.STRING, 100), column("target", LogicalTypes.STRING, 50)));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.BINARY, 100), column("target", LogicalTypes.BINARY, 50)));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.STRING, null), column("target", LogicalTypes.STRING, 50)));
    }

    @Test
    void shouldRejectNullableSourceForRequiredTarget() {
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                new Column("source", LogicalTypes.STRING, true, 100),
                new Column("target", LogicalTypes.STRING, false, 100)));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                new Column("source", LogicalTypes.STRING, false, 100),
                new Column("target", LogicalTypes.STRING, true, 100)));
    }

    @Test
    void shouldAllowBooleanToNumericTargetForCrossDatabasePlanning() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.BOOLEAN), column("target", LogicalTypes.INTEGER)));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.BOOLEAN), column("target", LogicalTypes.decimal(1, 0))));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.BOOLEAN), column("target", LogicalTypes.decimal(1, 1))));
    }

    @Test
    void shouldAllowDateToTimestampTarget() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.DATE), column("target", LogicalTypes.TIMESTAMP)));
    }

    @Test
    void shouldAllowFloatToDoubleOnly() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.FLOAT), column("target", LogicalTypes.DOUBLE)));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.DOUBLE), column("target", LogicalTypes.FLOAT)));
    }


    @Test
    void shouldRequireNoTemporalPrecisionLoss() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.timestamp(3)), column("target", LogicalTypes.timestamp(6))));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.timestamp(9)), column("target", LogicalTypes.timestamp(6))));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.time(6)), column("target", LogicalTypes.time(3))));
    }

    @Test
    void shouldAllowFixedToVariableWidthWithoutAllowingTruncation() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.charType(16)), column("target", LogicalTypes.varchar(32))));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.charType(32)), column("target", LogicalTypes.varchar(16))));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", LogicalTypes.fixedBinary(8)), column("target", LogicalTypes.varbinary(16))));
    }

    private Column column(String name, LogicalType type) {
        return column(name, type, null);
    }

    private Column column(String name, LogicalType type, Integer length) {
        return new Column(name, type, true, length);
    }
}
