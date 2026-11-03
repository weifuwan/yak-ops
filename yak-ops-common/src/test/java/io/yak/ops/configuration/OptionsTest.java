package io.yak.ops.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OptionsTest {

    @Test
    void typedOptionReadsItsDefaultWithoutAddingAnExplicitConfigurationEntry() {
        Option<Integer> option = Options.key("workers").intType().defaultValue(2);
        Configuration configuration = new Configuration();

        assertEquals("workers", option.key());
        assertEquals(Integer.class, option.getClazz());
        assertTrue(option.hasDefaultValue());
        assertEquals(2, (int) configuration.get(option));
        assertFalse(configuration.contains(option));

        configuration.setString("workers", "4");
        assertEquals(4, (int) configuration.get(option));
        assertTrue(configuration.contains(option));
    }

    @Test
    void noDefaultOptionStaysAbsentUntilExplicitlySet() {
        Option<String> option = Options.key("name").stringType().noDefaultValue();
        Configuration configuration = new Configuration();

        assertFalse(option.hasDefaultValue());
        assertNull(option.defaultValue());
        assertNull(configuration.get(option));
        assertTrue(configuration.getOptional(option).isEmpty());

        configuration.set(option, "yak-flow");
        assertEquals("yak-flow", configuration.get(option));
        assertTrue(configuration.removeConfig(option));
        assertNull(configuration.get(option));
    }

    @Test
    void invalidOptionDeclarationsFailBeforeConfigurationIsUsed() {
        assertThrows(IllegalArgumentException.class, () -> Options.key(""));
        assertThrows(IllegalArgumentException.class, () -> Options.key(" "));
        assertThrows(IllegalArgumentException.class, () -> Options.key(null));
        assertThrows(NullPointerException.class,
                () -> Options.key("workers").intType().defaultValue(null));
        assertThrows(NullPointerException.class,
                () -> Options.key("mode").enumType(null));
        assertThrows(NullPointerException.class,
                () -> new Option<>("workers", com.fasterxml.jackson.databind.type.TypeFactory.defaultInstance().constructType(Integer.class), null, true));
    }
}
