package io.yak.ops.flow.api.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class YakLogicalTypeTest {

    @Test
    void shouldExposeBasicTypeKind() {
        assertEquals(YakTypeKind.BIGINT, YakTypes.BIGINT.kind());
        assertEquals(YakTypeKind.TIMESTAMP, YakTypes.TIMESTAMP.kind());
    }

    @Test
    void shouldKeepDecimalPrecisionAndScaleInsideType() {
        YakDecimalType decimal = YakTypes.decimal(10, 2);

        assertEquals(YakTypeKind.DECIMAL, decimal.kind());
        assertEquals(10, decimal.precision());
        assertEquals(2, decimal.scale());
        assertEquals(decimal, YakTypes.decimal(10, 2));
        assertNotEquals(decimal, YakTypes.decimal(12, 2));
    }

    @Test
    void shouldAllowUnknownDecimalMetadata() {
        assertEquals(new YakDecimalType(null, null), YakTypes.decimal(null, null));
    }

    @Test
    void shouldRejectInvalidDecimalDefinition() {
        assertThrows(IllegalArgumentException.class, () -> YakTypes.decimal(0, 0));
        assertThrows(IllegalArgumentException.class, () -> YakTypes.decimal(10, -1));
        assertThrows(IllegalArgumentException.class, () -> YakTypes.decimal(2, 3));
    }

    @Test
    void shouldRejectDecimalAsBasicType() {
        assertThrows(IllegalArgumentException.class, () -> new YakBasicType(YakTypeKind.DECIMAL));
    }
}
