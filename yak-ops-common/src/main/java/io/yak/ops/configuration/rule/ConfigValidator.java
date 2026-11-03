package io.yak.ops.configuration.rule;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.validation.ConfigValidationError;
import io.yak.ops.configuration.validation.ConfigValidationResult;
import io.yak.ops.configuration.validation.OptionValidationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates declarative option rules without accessing external resources.
 *
 * <p>Unknown options are checked separately so components may compose common and plugin-specific
 * option rules before applying a strict allowlist.
 */
public final class ConfigValidator {

    private final ReadonlyConfig config;

    private ConfigValidator(ReadonlyConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public static ConfigValidator of(ReadonlyConfig config) {
        return new ConfigValidator(config);
    }

    public void validate(OptionRule rule) {
        ConfigValidationResult result = validateResult(rule);
        if (!result.valid()) {
            throw new OptionValidationException(result.errors());
        }
    }

    public ConfigValidationResult validateResult(OptionRule rule) {
        List<ConfigValidationError> errors = new ArrayList<>();
        collectErrors(Objects.requireNonNull(rule, "rule"), errors);
        return errors.isEmpty() ? ConfigValidationResult.success() : ConfigValidationResult.failure(errors);
    }

    private void collectErrors(OptionRule rule, List<ConfigValidationError> errors) {
        Set<String> missing = new HashSet<>();
        Set<String> invalidTypes = new HashSet<>();
        Set<String> checkedTypes = new HashSet<>();
        for (RequiredOption group : rule.getRequiredOptions()) {
            if (group.kind() == RequiredOption.Kind.CONDITIONAL) {
                try {
                    if (!group.when().evaluate(config)) {
                        continue;
                    }
                } catch (RuntimeException exception) {
                    typeError(String.join(",", group.when().condition().optionKeys()), errors, invalidTypes);
                    continue;
                }
            }
            List<Option<?>> present =
                    group.options().stream().filter(config::contains).toList();
            boolean valid =
                    switch (group.kind()) {
                        case REQUIRED, CONDITIONAL ->
                            present.size() == group.options().size();
                        case EXCLUSIVE -> present.size() == 1;
                        case BUNDLED ->
                            present.isEmpty()
                                    || present.size() == group.options().size();
                    };
            if (!valid) {
                String path = group.options().stream()
                        .map(Option::key)
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");
                errors.add(new ConfigValidationError(path, group.kind().name(), "Option group constraint failed"));
                if (group.kind() == RequiredOption.Kind.REQUIRED || group.kind() == RequiredOption.Kind.CONDITIONAL) {
                    group.options().stream()
                            .filter(option -> !config.contains(option))
                            .map(Option::key)
                            .forEach(missing::add);
                }
            }
            for (Option<?> option : present) {
                checkOption(option, errors, invalidTypes, checkedTypes);
            }
        }
        for (Option<?> option : rule.getOptionalOptions()) {
            if (config.contains(option)) {
                checkOption(option, errors, invalidTypes, checkedTypes);
            }
        }
        for (Condition<?> condition : rule.getValueConstraints()) {
            if (!condition.optionKeys().stream().allMatch(config::containsKey)
                    || condition.optionKeys().stream().anyMatch(missing::contains)
                    || condition.optionKeys().stream().anyMatch(invalidTypes::contains)) {
                continue;
            }
            try {
                if (!condition.evaluate(config)) {
                    errors.add(new ConfigValidationError(
                            String.join(",", condition.optionKeys()), "VALUE", condition.description()));
                }
            } catch (RuntimeException exception) {
                typeError(String.join(",", condition.optionKeys()), errors, invalidTypes);
            }
        }
        for (ConditionRule conditionRule : rule.getConditionRules()) {
            try {
                if (conditionRule.when().evaluate(config)) {
                    collectErrors(conditionRule.optionRule(), errors);
                }
            } catch (RuntimeException exception) {
                typeError(String.join(",", conditionRule.when().condition().optionKeys()), errors, invalidTypes);
            }
        }
    }

    private void checkOption(
            Option<?> option, List<ConfigValidationError> errors, Set<String> invalidTypes, Set<String> checkedTypes) {
        if (!checkedTypes.add(option.key())) {
            return;
        }
        try {
            Object value = config.get(option);
            if (!option.allowedValues().isEmpty() && !option.allowedValues().contains(value)) {
                errors.add(new ConfigValidationError(option.key(), "SINGLE_CHOICE", "Invalid option choice"));
            }
        } catch (RuntimeException exception) {
            typeError(option.key(), errors, invalidTypes);
        }
    }

    private static void typeError(String path, List<ConfigValidationError> errors, Set<String> invalidTypes) {
        if (invalidTypes.add(path)) {
            errors.add(new ConfigValidationError(path, "TYPE", "Invalid option value type"));
        }
    }

    /** Rejects keys absent from the declared option set, including undeclared nested paths. */
    public static void validateUnknownKeys(ReadonlyConfig config, OptionRule rule, String component) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(rule, "rule");
        Set<String> declared = declaredKeys(rule);
        List<ConfigValidationError> errors = new ArrayList<>();
        checkPaths(config.getSourceMap(), "", declared, errors);
        if (!errors.isEmpty()) {
            throw new OptionValidationException(errors);
        }
    }

    private static void checkPaths(
            Map<String, ?> values, String prefix, Set<String> declared, List<ConfigValidationError> errors) {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (declared.contains(path)) {
                continue;
            }
            boolean namespace = declared.stream().anyMatch(key -> key.startsWith(path + "."));
            if (!namespace) {
                errors.add(new ConfigValidationError(path, "UNKNOWN", "Unknown configuration option"));
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, ?> valuesByName = (Map<String, ?>) nested;
                checkPaths(valuesByName, path, declared, errors);
            } else {
                errors.add(new ConfigValidationError(path, "TYPE", "Configuration namespace must be an object"));
            }
        }
    }

    private static Set<String> declaredKeys(OptionRule rule) {
        Set<String> keys = new LinkedHashSet<>();
        rule.getOptionalOptions().forEach(option -> keys.add(option.key()));
        for (RequiredOption group : rule.getRequiredOptions()) {
            group.options().forEach(option -> keys.add(option.key()));
            if (group.when() != null) {
                keys.addAll(group.when().condition().optionKeys());
            }
        }
        rule.getValueConstraints().forEach(condition -> keys.addAll(condition.optionKeys()));
        for (ConditionRule ruleGroup : rule.getConditionRules()) {
            keys.addAll(ruleGroup.when().condition().optionKeys());
            keys.addAll(declaredKeys(ruleGroup.optionRule()));
        }
        return keys;
    }
}
