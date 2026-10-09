package io.yak.ops.plugin.database.jdbc.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakDataType;
import io.yak.ops.flow.api.row.YakTypes;
import org.junit.jupiter.api.Test;

class JdbcSchemaCompatibilityTest {

    @Test
    void shouldAllowIntegerWideningAndRejectNarrowing() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.INTEGER), column("target", YakTypes.BIGINT)));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.BIGINT), column("target", YakTypes.SMALLINT)));
    }

    @Test
    void shouldValidateIntegerToDecimalCapacity() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.INTEGER), column("target", YakTypes.decimal(12, 2))));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.BIGINT), column("target", YakTypes.decimal(18, 0))));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.BIGINT), column("target", YakTypes.decimal(null, null))));
    }

    @Test
    void shouldRejectDecimalPrecisionOrScaleLoss() {
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.decimal(10, 2)), column("target", YakTypes.decimal(9, 2))));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.decimal(10, 2)), column("target", YakTypes.decimal(12, 1))));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.decimal(10, 2)), column("target", YakTypes.decimal(12, 2))));
    }

    @Test
    void shouldPreserveUnknownDecimalCompatibility() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.decimal(null, null)),
                column("target", YakTypes.decimal(12, 2))));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.decimal(10, null)),
                column("target", YakTypes.decimal(12, null))));
    }

    @Test
    void shouldRejectSmallerStringAndBinaryCapacity() {
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.STRING, 100), column("target", YakTypes.STRING, 50)));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.BINARY, 100), column("target", YakTypes.BINARY, 50)));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.STRING, null), column("target", YakTypes.STRING, 50)));
    }

    @Test
    void shouldRejectNullableSourceForRequiredTarget() {
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                new YakColumn("source", YakTypes.STRING, true, 100),
                new YakColumn("target", YakTypes.STRING, false, 100)));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                new YakColumn("source", YakTypes.STRING, false, 100),
                new YakColumn("target", YakTypes.STRING, true, 100)));
    }

    @Test
    void shouldAllowBooleanToNumericTargetForCrossDatabasePlanning() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.BOOLEAN), column("target", YakTypes.INTEGER)));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.BOOLEAN), column("target", YakTypes.decimal(1, 0))));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.BOOLEAN), column("target", YakTypes.decimal(1, 1))));
    }

    @Test
    void shouldAllowDateToTimestampTarget() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.DATE), column("target", YakTypes.TIMESTAMP)));
    }

    @Test
    void shouldAllowFloatToDoubleOnly() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.FLOAT), column("target", YakTypes.DOUBLE)));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", YakTypes.DOUBLE), column("target", YakTypes.FLOAT)));
    }

    private YakColumn column(String name, YakDataType type) {
        return column(name, type, null);
    }

    private YakColumn column(String name, YakDataType type, Integer length) {
        return new YakColumn(name, type, true, length);
    }
}
