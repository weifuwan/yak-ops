package io.yak.ops.common.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ImmutablePropertyDelegateTest {

    private static final ImmutablePropertyDelegate immutablePropertyDelegate = new ImmutablePropertyDelegate();

    @Test
    void get() {
        Assertions.assertNull(immutablePropertyDelegate.get("null"));
    }

    @Test
    void testGetDefaultValue() {
        Assertions.assertEquals("default", immutablePropertyDelegate.get("null", "default"));
    }

    @Test
    void getPropertyKeys() {
        Assertions.assertNotNull(immutablePropertyDelegate.getPropertyKeys());
    }

    @Test
    void getOptional() {
        Assertions.assertFalse(immutablePropertyDelegate.getOptional("null").isPresent());
    }

    @Test
    void getInt() {
        Assertions.assertEquals(1, immutablePropertyDelegate.getInt("int.property"));
    }

    @Test
    void getIntDefault() {
        Assertions.assertEquals(2, immutablePropertyDelegate.getInt("int2.property", 2));
    }

    @Test
    void getLong() {
        Assertions.assertEquals(1, immutablePropertyDelegate.getLong("long.property"));
    }

    @Test
    void getLongDefault() {
        Assertions.assertEquals(2, immutablePropertyDelegate.getLong("long2.property", 2L));
    }

    @Test
    void getDouble() {
        Assertions.assertEquals(1.1, immutablePropertyDelegate.getDouble("double.property"));
    }

    @Test
    void getDoubleDefault() {
        Assertions.assertEquals(2.2, immutablePropertyDelegate.getDouble("double2.property", 2.2d));
    }

    @Test
    void getBoolean() {
        Assertions.assertEquals(true, immutablePropertyDelegate.getBoolean("boolean.property"));
    }

    @Test
    void getBooleanDefault() {
        Assertions.assertEquals(false, immutablePropertyDelegate.getBoolean("boolean2.property", false));
    }

}
