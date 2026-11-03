package io.yak.ops.configuration.rule;

import io.yak.ops.configuration.Option;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Declarative validation contract for the options accepted by one component. */
public final class OptionRule {

    private final List<Option<?>> optionalOptions;
    private final List<RequiredOption> requiredOptions;
    private final List<Condition<?>> valueConstraints;
    private final List<ConditionRule> conditionRules;

    private OptionRule(
            List<Option<?>> optionalOptions,
            List<RequiredOption> requiredOptions,
            List<Condition<?>> valueConstraints,
            List<ConditionRule> conditionRules) {
        this.optionalOptions = List.copyOf(optionalOptions);
        this.requiredOptions = List.copyOf(requiredOptions);
        this.valueConstraints = List.copyOf(valueConstraints);
        this.conditionRules = List.copyOf(conditionRules);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Option<?>> getOptionalOptions() {
        return optionalOptions;
    }

    public List<RequiredOption> getRequiredOptions() {
        return requiredOptions;
    }

    public List<Condition<?>> getValueConstraints() {
        return valueConstraints;
    }

    public List<ConditionRule> getConditionRules() {
        return conditionRules;
    }

    /** Builder for required, optional and conditional option groups. */
    public static final class Builder {
        private final List<Option<?>> optionalOptions = new ArrayList<>();
        private final List<RequiredOption> requiredOptions = new ArrayList<>();
        private final List<Condition<?>> valueConstraints = new ArrayList<>();
        private final List<ConditionRule> conditionRules = new ArrayList<>();

        private Builder() {}

        /** Includes the complete rules declared by another component. */
        public Builder include(OptionRule other) {
            Objects.requireNonNull(other, "other");
            optionalOptions.addAll(other.getOptionalOptions());
            requiredOptions.addAll(other.getRequiredOptions());
            valueConstraints.addAll(other.getValueConstraints());
            conditionRules.addAll(other.getConditionRules());
            return this;
        }

        public Builder required(Option<?>... options) {
            requiredOptions.add(new RequiredOption(RequiredOption.Kind.REQUIRED, Arrays.asList(options), null));
            return this;
        }

        public Builder required(Option<?> option, Condition<?> first, Condition<?>... conditions) {
            required(option);
            valueConstraints.add(Objects.requireNonNull(first, "first"));
            valueConstraints.addAll(Arrays.asList(conditions));
            return this;
        }

        public Builder required(Option<?> first, Option<?> second, Condition<?> condition, Condition<?>... conditions) {
            required(first, second);
            valueConstraints.add(Objects.requireNonNull(condition, "condition"));
            valueConstraints.addAll(Arrays.asList(conditions));
            return this;
        }

        public Builder optional(Option<?>... options) {
            optionalOptions.addAll(Arrays.asList(options));
            return this;
        }

        public Builder optional(Option<?> option, Condition<?> first, Condition<?>... conditions) {
            optional(option);
            valueConstraints.add(Objects.requireNonNull(first, "first"));
            valueConstraints.addAll(Arrays.asList(conditions));
            return this;
        }

        public Builder exclusive(Option<?>... options) {
            if (options.length < 2) {
                throw new IllegalArgumentException("Exclusive group requires at least two options");
            }
            requiredOptions.add(new RequiredOption(RequiredOption.Kind.EXCLUSIVE, Arrays.asList(options), null));
            return this;
        }

        public Builder bundled(Option<?>... options) {
            if (options.length < 2) {
                throw new IllegalArgumentException("Bundled group requires at least two options");
            }
            requiredOptions.add(new RequiredOption(RequiredOption.Kind.BUNDLED, Arrays.asList(options), null));
            return this;
        }

        public <T> Builder conditional(Option<T> option, T expected, Option<?>... options) {
            Expression when = Expression.of(Condition.of(option, expected));
            requiredOptions.add(new RequiredOption(RequiredOption.Kind.CONDITIONAL, Arrays.asList(options), when));
            return this;
        }

        public <T> Builder conditional(Option<T> option, T expected, Condition<?> first, Condition<?>... rest) {
            return conditionalRule(
                    option,
                    expected,
                    OptionRule.builder().withConstraints(first, rest).build());
        }

        public <T> Builder conditionalRule(Option<T> option, T expected, OptionRule rule) {
            return conditionalRule(Expression.of(Condition.of(option, expected)), rule);
        }

        public Builder conditionalRule(Expression expression, OptionRule rule) {
            conditionRules.add(new ConditionRule(expression, rule));
            return this;
        }

        private Builder withConstraints(Condition<?> first, Condition<?>... rest) {
            valueConstraints.add(first);
            valueConstraints.addAll(Arrays.asList(rest));
            return this;
        }

        public OptionRule build() {
            for (Option<?> option : optionalOptions) {
                Objects.requireNonNull(option, "optional option");
            }
            for (Condition<?> constraint : valueConstraints) {
                Objects.requireNonNull(constraint, "constraint");
            }
            return new OptionRule(optionalOptions, requiredOptions, valueConstraints, conditionRules);
        }
    }
}
