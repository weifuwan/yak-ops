package io.yak.ops.core.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LogicalTypesTest {

    @Test
    void baseAndDecimalTypesHaveStableValueSemantics() {
        assertEquals(TypeKind.INTEGER, LogicalTypes.INTEGER.kind());
        assertEquals(new DecimalType(18, 2), LogicalTypes.decimal(18, 2));
        assertEquals(TypeKind.DECIMAL, LogicalTypes.decimal(10, 0).kind());
        assertNull(LogicalTypes.decimal(null, null).precision());
    }

    @Test
    void invalidDecimalParametersAndBasicDecimalAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BasicType(TypeKind.DECIMAL));
        assertThrows(IllegalArgumentException.class, () -> LogicalTypes.decimal(0, 2));
        assertThrows(IllegalArgumentException.class, () -> LogicalTypes.decimal(3, 4));
        assertThrows(IllegalArgumentException.class, () -> LogicalTypes.decimal(10, -1));
    }
}
