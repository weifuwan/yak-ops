package io.yak.ops.core.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfigOptionsTest {

    @Test
    void typedOptionReadsItsDefaultWithoutAddingAnExplicitConfigurationEntry() {
        ConfigOption<Integer> option = ConfigOptions.key("workers").intType().defaultValue(2);
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
        ConfigOption<String> option = ConfigOptions.key("name").stringType().noDefaultValue();
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
        assertThrows(IllegalArgumentException.class, () -> ConfigOptions.key(""));
        assertThrows(IllegalArgumentException.class, () -> ConfigOptions.key(" "));
        assertThrows(IllegalArgumentException.class, () -> ConfigOptions.key(null));
        assertThrows(NullPointerException.class,
                () -> ConfigOptions.key("workers").intType().defaultValue(null));
        assertThrows(NullPointerException.class,
                () -> ConfigOptions.key("mode").enumType(null));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigOption<>("workers", Integer.class, null, true));
    }
}
