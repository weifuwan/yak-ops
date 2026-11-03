package io.yak.ops.configuration.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.validation.ConfigValidationResult;
import io.yak.ops.configuration.validation.OptionValidationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Covers rule composition, unknown keys, and sanitized validation failures. */
class ConfigValidatorTest {

    private static final Option<String> MODE = Options.key("mode").stringType().noDefaultValue();
    private static final Option<String> HOST = Options.key("host").stringType().noDefaultValue();
    private static final Option<String> URL = Options.key("url").stringType().noDefaultValue();
    private static final Option<Integer> PORT = Options.key("port").intType().noDefaultValue();
    private static final Option<Integer> BATCH = Options.key("batch").intType().noDefaultValue();
    private static final Option<Integer> LIMIT = Options.key("limit").intType().noDefaultValue();

    @Test
    void requiredOptionalExclusiveAndBundledOptionsAreChecked() {
        OptionRule rule = OptionRule.builder()
                .required(MODE, Conditions.notBlank(MODE))
                .optional(BATCH, Conditions.greaterThan(BATCH, 0))
                .exclusive(HOST, URL)
                .bundled(PORT, LIMIT)
                .build();

        ConfigValidationResult bad = ConfigValidator.of(ReadonlyConfig.fromMap(
                        Map.of("mode", "READ", "host", "server", "url", "jdbc:foo", "port", 1, "batch", 0)))
                .validateResult(rule);
        assertFalse(bad.valid());
        assertEquals(3, bad.errors().size());
        assertTrue(bad.errors().stream().anyMatch(error -> error.rule().equals("EXCLUSIVE")));
        assertTrue(bad.errors().stream().anyMatch(error -> error.rule().equals("BUNDLED")));
        assertTrue(bad.errors().stream().anyMatch(error -> error.rule().equals("VALUE")));

        ConfigValidator.of(ReadonlyConfig.fromMap(
                        Map.of("mode", "READ", "url", "jdbc:foo", "port", 1, "limit", 2, "batch", 1)))
                .validate(rule);
    }

    @Test
    void conditionallyRequiredOptionsAndNumericComparisonsAreChecked() {
        OptionRule rule = OptionRule.builder()
                .required(MODE)
                .optional(PORT, LIMIT)
                .conditional(MODE, "TIMESTAMP", LIMIT)
                .optional(PORT, Conditions.greaterOrEqual(PORT, 1)
                        .and(Conditions.lessOrEqual(PORT, 65535)))
                .optional(LIMIT, Conditions.greaterThanField(LIMIT, PORT))
                .build();

        ConfigValidationResult result =
                ConfigValidator.of(ReadonlyConfig.fromMap(Map.of("mode", "TIMESTAMP", "port", 9000)))
                        .validateResult(rule);
        assertFalse(result.valid());
        assertEquals("CONDITIONAL", result.errors().getFirst().rule());
        ConfigValidator.of(ReadonlyConfig.fromMap(Map.of("mode", "TIMESTAMP", "port", 9000, "limit", 9001)))
                .validate(rule);
    }

    @Test
    void singleChoiceAndExtensionAreEvaluated() {
        Option<String> format =
                Options.key("format").singleChoice(String.class, List.of("JSON", "CSV")).noDefaultValue();
        OptionRule rule = OptionRule.builder()
                .required(format)
                .optional(HOST, Conditions.extension(HOST, new ConditionExtension<>() {
                    @Override
                    public String description() {
                        return "Host must use a DNS label";
                    }

                    @Override
                    public boolean evaluate(ReadonlyConfig config, String value) {
                        return !value.contains(" ");
                    }
                }))
                .build();
        assertFalse(ConfigValidator.of(ReadonlyConfig.fromMap(Map.of("format", "XML")))
                .validateResult(rule).valid());
        assertFalse(ConfigValidator.of(ReadonlyConfig.fromMap(Map.of("format", "JSON", "host", "two words")))
                .validateResult(rule).valid());
        ConfigValidator.of(ReadonlyConfig.fromMap(Map.of("format", "CSV", "host", "example")))
                .validate(rule);
    }

    @Test
    void unknownNestedPathsFailWithoutRevealingValues() {
        Option<String> table = Options.key("source.table").stringType().noDefaultValue();
        OptionRule rule = OptionRule.builder().required(table).build();
        ReadonlyConfig config = ReadonlyConfig.fromMap(
                Map.of("source", Map.of("table", "orders", "unexpected", "sensitive-value")));

        OptionValidationException error = assertThrows(
                OptionValidationException.class, () -> ConfigValidator.validateUnknownKeys(config, rule, "source"));
        assertEquals("source.unexpected", error.errors().getFirst().path());
        assertFalse(error.getMessage().contains("sensitive-value"));
        assertThrows(OptionValidationException.class, () -> ConfigValidator.validateUnknownKeys(
                ReadonlyConfig.fromMap(Map.of("source", "invalid")), rule, "source"));
    }

    @Test
    void declaredMapContentsAreNotTreatedAsUnknownKeys() {
        Option<Map<String, String>> properties = Options.key("properties").mapType().noDefaultValue();
        OptionRule rule = OptionRule.builder().optional(properties).build();
        ConfigValidator.validateUnknownKeys(
                ReadonlyConfig.fromMap(Map.of("properties", Map.of("vendor.setting", "yes"))),
                rule,
                "jdbc");
    }
}
