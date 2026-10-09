package io.yak.ops.core.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.RuntimeExecutionMode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests the public Configuration behavior, not the names or packages of its implementation. */
class ConfigurationTest {

    @Test
    @SuppressWarnings("deprecation")
    void defaultsAreNotStoredAndLegacyAliasesShareTheSameValues() {
        Configuration configuration = new Configuration();

        assertEquals(1, configuration.get(CoreOptions.DEFAULT_PARALLELISM));
        assertEquals(Duration.ZERO, configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL));
        assertTrue(configuration.getOptional(CoreOptions.DEFAULT_PARALLELISM).isEmpty());
        assertTrue(configuration.getOptional(CheckpointingOptions.CHECKPOINTING_INTERVAL).isEmpty());
        assertTrue(configuration.keySet().isEmpty());
        assertTrue(configuration.toMap().isEmpty());

        configuration.set(ExecutionOptions.DEFAULT_PARALLELISM, 4);
        configuration.set(ExecutionOptions.CHECKPOINT_INTERVAL, Duration.ofSeconds(5));

        assertEquals(4, configuration.get(CoreOptions.DEFAULT_PARALLELISM));
        assertEquals(Duration.ofSeconds(5), configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL));
        assertEquals(4, configuration.getOptional(CoreOptions.DEFAULT_PARALLELISM).orElseThrow());
        assertEquals(2, configuration.keySet().size());
    }

    @Test
    void persistedStringsAreConvertedToTypedOptions() {
        Configuration configuration = Configuration.fromMap(Map.of(
                "workers", " 42 ",
                "offset", "9000000000",
                "factor", "1.25",
                "ratio", "0.5",
                "enabled", "TrUe",
                "mode", " streaming ",
                "label", "keep spaces"));

        assertEquals(42, configuration.get(ConfigOptions.key("workers").intType().noDefaultValue()));
        assertEquals(9_000_000_000L, configuration.get(ConfigOptions.key("offset").longType().noDefaultValue()));
        assertEquals(1.25d, configuration.get(ConfigOptions.key("factor").doubleType().noDefaultValue()));
        assertEquals(0.5f, configuration.get(ConfigOptions.key("ratio").floatType().noDefaultValue()));
        assertTrue(configuration.get(ConfigOptions.key("enabled").booleanType().noDefaultValue()));
        assertEquals(RuntimeExecutionMode.STREAMING,
                configuration.get(ConfigOptions.key("mode").enumType(RuntimeExecutionMode.class).noDefaultValue()));
        assertEquals("keep spaces", configuration.get(ConfigOptions.key("label").stringType().noDefaultValue()));
        assertEquals(" 42 ", configuration.getString("workers", "missing"));
        assertEquals(" 42 ", configuration.toMap().get("workers"));
    }

    @Test
    void durationSupportsIsoAndReadableUnits() {
        ConfigOption<Duration> duration = ConfigOptions.key("checkpoint.timeout").durationType().noDefaultValue();
        Configuration configuration = new Configuration();

        Map<String, Duration> samples = Map.of(
                "PT1H30M", Duration.ofMinutes(90),
                "500ms", Duration.ofMillis(500),
                "12ns", Duration.ofNanos(12),
                "50us", Duration.ofNanos(50_000),
                "10s", Duration.ofSeconds(10),
                "2min", Duration.ofMinutes(2),
                "1h", Duration.ofHours(1),
                "2d", Duration.ofDays(2));
        for (var sample : samples.entrySet()) {
            configuration.setString(duration.key(), sample.getKey());
            assertEquals(sample.getValue(), configuration.get(duration), sample.getKey());
        }
    }

    @Test
    void malformedTypedValuesFailWithTheConfigurationKeyAndPreserveTheRawValue() {
        assertInvalidValue("workers", "not-a-number", ConfigOptions.key("workers").intType().noDefaultValue());
        assertInvalidValue("workers", "2147483648", ConfigOptions.key("workers").intType().noDefaultValue());
        assertInvalidValue("enabled", "sometimes", ConfigOptions.key("enabled").booleanType().noDefaultValue());
        assertInvalidValue("mode", "unsupported",
                ConfigOptions.key("mode").enumType(RuntimeExecutionMode.class).noDefaultValue());
        assertInvalidValue("timeout", "10fortnights",
                ConfigOptions.key("timeout").durationType().noDefaultValue());
        assertInvalidValue("timeout", "9223372036854775807d",
                ConfigOptions.key("timeout").durationType().noDefaultValue());
    }

    @Test
    void copyConstructorAndCloneIsolateSubsequentChanges() {
        ConfigOption<Integer> workers = ConfigOptions.key("workers").intType().noDefaultValue();
        Configuration original = new Configuration().set(workers, 2);
        Configuration copy = new Configuration(original);
        Configuration clone = original.clone();

        assertNotSame(original, copy);
        assertNotSame(original, clone);
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());

        original.set(workers, 4);
        assertEquals(2, copy.get(workers));
        assertEquals(2, clone.get(workers));

        copy.set(workers, 8);
        clone.setString("new-key", "clone-only");
        assertEquals(4, original.get(workers));
        assertFalse(original.containsKey("new-key"));
        assertNotEquals(original, copy);
        assertNotEquals(original, clone);
    }

    @Test
    void addAllOverwritesOnlyExplicitKeysAndDoesNotShareTheSourceMap() {
        ConfigOption<Integer> workers = ConfigOptions.key("workers").intType().defaultValue(1);
        ConfigOption<String> mode = ConfigOptions.key("mode").stringType().defaultValue("BATCH");
        Configuration target = new Configuration().set(workers, 2);
        Configuration source = new Configuration().set(workers, 3);
        source.setString("label", "source");

        target.addAll(source);

        assertEquals(3, target.get(workers));
        assertEquals("source", target.getString("label", "missing"));
        assertEquals("BATCH", target.get(mode));
        assertFalse(target.contains(mode));

        source.set(workers, 9);
        source.setString("label", "changed");
        assertEquals(3, target.get(workers));
        assertEquals("source", target.getString("label", "missing"));
    }

    @Test
    void removingOptionsChangesOnlyExplicitState() {
        ConfigOption<Integer> workers = ConfigOptions.key("workers").intType().defaultValue(2);
        Configuration configuration = new Configuration();

        assertFalse(configuration.contains(workers));
        assertFalse(configuration.removeConfig(workers));
        assertEquals(2, configuration.get(workers));

        configuration.set(workers, 5);
        assertTrue(configuration.containsKey(workers.key()));
        assertTrue(configuration.contains(workers));
        assertTrue(configuration.removeConfig(workers));
        assertFalse(configuration.contains(workers));
        assertFalse(configuration.removeKey(workers.key()));
        assertEquals(2, configuration.get(workers));

        configuration.setString("another", "value");
        assertTrue(configuration.removeKey("another"));
        assertFalse(configuration.containsKey("another"));
    }

    @Test
    void exportedMapAndKeySetCannotMutateStoredConfiguration() {
        Configuration configuration = Configuration.fromMap(Map.of("a", "one", "b", "two"));
        Set<String> keys = configuration.keySet();
        Map<String, String> exported = configuration.toMap();

        assertThrows(UnsupportedOperationException.class, () -> keys.add("other"));
        exported.put("a", "changed");
        exported.put("new", "unexpected");
        configuration.setString("c", "three");

        assertEquals("one", configuration.getString("a", "missing"));
        assertFalse(configuration.containsKey("new"));
        assertEquals(Set.of("a", "b"), keys);
        assertEquals(Set.of("a", "b", "c"), configuration.keySet());
    }

    @Test
    void diagnosticToStringMasksCredentialsWithoutChangingStoredValues() {
        Configuration configuration = Configuration.fromMap(Map.of(
                "database.password", "password-visible-in-map",
                "AUTH_TOKEN", "token-visible-in-map",
                "access-key", "key-visible-in-map",
                "job.name", "public-job"));

        String diagnostic = configuration.toString();

        assertFalse(diagnostic.contains("password-visible-in-map"));
        assertFalse(diagnostic.contains("token-visible-in-map"));
        assertFalse(diagnostic.contains("key-visible-in-map"));
        assertTrue(diagnostic.contains("job.name=public-job"));
        assertTrue(diagnostic.contains("database.password=******"));
        assertEquals("password-visible-in-map", configuration.toMap().get("database.password"));
    }

    @Test
    void configurationSurvivesJavaSerializationWithItsTypedValues() throws Exception {
        Configuration original = new Configuration();
        original.set(CoreOptions.DEFAULT_PARALLELISM, 3);
        original.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofMillis(250));
        original.set(ExecutionOptions.RUNTIME_MODE, RuntimeExecutionMode.STREAMING);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        Configuration restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (Configuration) input.readObject();
        }

        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
        assertEquals(3, restored.get(CoreOptions.DEFAULT_PARALLELISM));
        assertEquals(Duration.ofMillis(250), restored.get(CheckpointingOptions.CHECKPOINTING_INTERVAL));
        assertEquals(RuntimeExecutionMode.STREAMING, restored.get(ExecutionOptions.RUNTIME_MODE));
    }

    @Test
    void invalidRawKeysAndNullWritesDoNotCreateEntries() {
        Configuration configuration = new Configuration();
        ConfigOption<Integer> workers = ConfigOptions.key("workers").intType().noDefaultValue();

        assertThrows(IllegalArgumentException.class, () -> configuration.setString(" ", "1"));
        assertThrows(IllegalArgumentException.class, () -> configuration.getString(null, "unused"));
        assertThrows(NullPointerException.class, () -> configuration.setString("workers", null));
        assertThrows(NullPointerException.class, () -> configuration.set(workers, null));
        assertThrows(NullPointerException.class, () -> Configuration.fromMap(null));
        assertFalse(configuration.contains(workers));
        assertTrue(configuration.keySet().isEmpty());
    }

    private static <T> void assertInvalidValue(String key, String raw, ConfigOption<T> option) {
        Configuration configuration = new Configuration();
        configuration.setString(key, raw);

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> configuration.get(option));
        assertTrue(failure.getMessage().contains(key));
        assertEquals(raw, configuration.getString(key, "missing"));
        assertTrue(configuration.contains(option));
    }
}
