package io.yak.ops.configuration.rule;

import io.yak.ops.configuration.Option;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provides safe, declarative Option metadata for REST and form consumers.
 *
 * <p>Secret values are never returned. The complete validation rules remain authoritative
 * on the server even when a client only needs defaults and allowed choices.
 */
public final class OptionRuleMetadata {

    private OptionRuleMetadata() {}

    /** Returns every declared option exactly once, preserving declaration order. */
    public static List<OptionDefinition> describe(OptionRule rule) {
        Map<String, OptionDefinition> definitions = new LinkedHashMap<>();
        collect(rule, definitions, false);
        return List.copyOf(definitions.values());
    }

    private static void collect(OptionRule rule, Map<String, OptionDefinition> definitions, boolean conditional) {
        for (RequiredOption required : rule.getRequiredOptions()) {
            boolean unconditional = !conditional && required.kind() == RequiredOption.Kind.REQUIRED;
            for (Option<?> option : required.options()) {
                add(definitions, option, unconditional);
            }
        }
        for (Option<?> option : rule.getOptionalOptions()) {
            add(definitions, option, false);
        }
        for (Condition<?> condition : rule.getValueConstraints()) {
            if (condition.option() != null) {
                add(definitions, condition.option(), false);
            }
        }
        for (ConditionRule conditionRule : rule.getConditionRules()) {
            collect(conditionRule.optionRule(), definitions, true);
        }
    }

    private static void add(Map<String, OptionDefinition> definitions, Option<?> option, boolean required) {
        OptionDefinition existing = definitions.get(option.key());
        if (existing != null) {
            if (required && !existing.required()) {
                definitions.put(
                        option.key(),
                        new OptionDefinition(
                                existing.key(),
                                existing.type(),
                                true,
                                existing.defaultValue(),
                                existing.allowedValues(),
                                existing.description()));
            }
            return;
        }
        String lower = option.key().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        boolean secret = lower.contains("password")
                || lower.contains("passphrase")
                || lower.contains("privatekey")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("credential");
        Object defaultValue = secret || !option.hasDefaultValue() ? null : option.defaultValue();
        List<String> values = new ArrayList<>();
        for (Object choice : option.allowedValues()) {
            values.add(choice instanceof Enum<?> enumeration ? enumeration.name() : String.valueOf(choice));
        }
        String type = option.valueType().isCollectionLikeType()
                ? "LIST"
                : option.valueType().isMapLikeType()
                        ? "MAP"
                        : option.getClazz().isEnum()
                                ? "ENUM"
                                : option.getClazz().getSimpleName().toUpperCase(Locale.ROOT);
        definitions.put(
                option.key(),
                new OptionDefinition(
                        option.key(), type, required, defaultValue, List.copyOf(values), option.getDescription()));
    }

    /** Safe field metadata independent of configured values. */
    public record OptionDefinition(
            String key,
            String type,
            boolean required,
            Object defaultValue,
            List<String> allowedValues,
            String description) {}
}
