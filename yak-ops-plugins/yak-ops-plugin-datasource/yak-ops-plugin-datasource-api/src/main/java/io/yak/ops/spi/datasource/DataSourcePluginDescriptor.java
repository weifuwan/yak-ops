package io.yak.ops.spi.datasource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable datasource plugin metadata and connection-form contract. */
public record DataSourcePluginDescriptor(
        String type,
        String displayName,
        Set<String> aliases,
        String apiVersion,
        Set<DataSourceCapability> capabilities,
        ConnectionForm connectionForm,
        boolean installRequired,
        String installHint) {

    public static final String CURRENT_API_VERSION = "1";

    public DataSourcePluginDescriptor {
        type = normalizeType(type);
        displayName = normalize(displayName, type);
        aliases = immutableAliases(type, aliases);
        apiVersion = normalize(apiVersion, CURRENT_API_VERSION);
        capabilities = immutableCapabilities(capabilities);
        connectionForm = connectionForm == null ? ConnectionForm.empty() : connectionForm;
        installHint = trimToNull(installHint);
    }

    public DataSourcePluginDescriptor(
            String type,
            String displayName,
            String apiVersion,
            Set<DataSourceCapability> capabilities,
            ConnectionForm connectionForm,
            boolean installRequired,
            String installHint) {
        this(type, displayName, Set.of(), apiVersion, capabilities, connectionForm, installRequired, installHint);
    }

    public boolean matchesType(String value) {
        String normalized = normalizeType(value);
        return type.equals(normalized) || aliases.contains(normalized);
    }

    public boolean supports(DataSourceCapability capability) {
        return capability != null && capabilities.contains(capability);
    }

    public static String normalizeType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new IllegalArgumentException("plugin type must not be blank");
        return normalized.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    public Set<String> secretFieldKeys() {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        for (FormField field : connectionForm.allFields()) {
            if (field != null && field.secret()) keys.add(field.key());
        }
        return Collections.unmodifiableSet(keys);
    }

    public record ConnectionForm(List<FormSection> sections, List<FormField> legacyFields) {
        public ConnectionForm {
            sections = sections == null ? List.of() : List.copyOf(sections);
            legacyFields = legacyFields == null ? List.of() : List.copyOf(legacyFields);
        }

        public static ConnectionForm empty() {
            return new ConnectionForm(List.of(), List.of());
        }

        public List<FormField> allFields() {
            List<FormField> fields = new ArrayList<>(legacyFields);
            for (FormSection section : sections) {
                if (section != null) fields.addAll(section.fields());
            }
            return List.copyOf(fields);
        }
    }

    public record FormSection(
            String key,
            String title,
            String description,
            boolean collapsible,
            boolean defaultExpanded,
            List<FormField> fields) {
        public FormSection {
            key = requireText(key, "section key");
            title = requireText(title, "section title");
            description = description == null ? "" : description;
            fields = fields == null ? List.of() : List.copyOf(fields);
        }

        public FormSection withFields(List<FormField> replacement) {
            return new FormSection(key, title, description, collapsible, defaultExpanded, replacement);
        }
    }

    public record FormField(
            String key,
            String label,
            FieldType type,
            String placeholder,
            Object defaultValue,
            List<FormOption> options,
            List<FormRule> rules,
            List<String> dependsOn,
            List<VisibilityCondition> visibleWhen,
            JdbcUrlLinkage jdbcUrlLinkage) {
        public FormField {
            key = requireText(key, "field key");
            label = requireText(label, "field label");
            type = Objects.requireNonNull(type, "field type");
            placeholder = trimToNull(placeholder);
            defaultValue = immutableValue(defaultValue);
            options = options == null ? List.of() : List.copyOf(options);
            rules = rules == null ? List.of() : List.copyOf(rules);
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
            visibleWhen = visibleWhen == null ? List.of() : List.copyOf(visibleWhen);
        }

        public boolean secret() {
            return type == FieldType.PASSWORD;
        }

        public FormField withType(FieldType replacement) {
            return new FormField(
                    key,
                    label,
                    replacement,
                    placeholder,
                    defaultValue,
                    options,
                    rules,
                    dependsOn,
                    visibleWhen,
                    jdbcUrlLinkage);
        }

        public FormField withPlaceholder(String replacement) {
            return new FormField(
                    key,
                    label,
                    type,
                    replacement,
                    defaultValue,
                    options,
                    rules,
                    dependsOn,
                    visibleWhen,
                    jdbcUrlLinkage);
        }

        public FormField withDependsOn(List<String> replacement) {
            return new FormField(
                    key,
                    label,
                    type,
                    placeholder,
                    defaultValue,
                    options,
                    rules,
                    replacement,
                    visibleWhen,
                    jdbcUrlLinkage);
        }

        public FormField withVisibleWhen(List<VisibilityCondition> replacement) {
            return new FormField(
                    key,
                    label,
                    type,
                    placeholder,
                    defaultValue,
                    options,
                    rules,
                    dependsOn,
                    replacement,
                    jdbcUrlLinkage);
        }

        public FormField withJdbcUrlLinkage(JdbcUrlLinkage replacement) {
            return new FormField(
                    key, label, type, placeholder, defaultValue, options, rules, dependsOn, visibleWhen, replacement);
        }
    }

    public enum FieldType {
        INPUT,
        PASSWORD,
        SELECT,
        NUMBER,
        SWITCH,
        TEXTAREA,
        CUSTOM_SELECT,
        DRIVER,
        SSH,
        JDBC_URL
    }

    public record FormOption(String label, Object value) {
        public FormOption {
            label = requireText(label, "option label");
            value = immutableValue(value);
        }
    }

    public record FormRule(Boolean required, String pattern, Integer min, Integer max, String message) {
        public FormRule {
            pattern = trimToNull(pattern);
            message = trimToNull(message);
        }
    }

    public record VisibilityCondition(String field, VisibilityOperator operator, Object value, List<Object> values) {
        public VisibilityCondition {
            field = trimToNull(field);
            operator = Objects.requireNonNull(operator, "visibility operator");
            value = immutableValue(value);
            values = values == null
                    ? List.of()
                    : values.stream()
                            .map(DataSourcePluginDescriptor::immutableValue)
                            .toList();
        }
    }

    public enum VisibilityOperator {
        EQUALS,
        NOT_EQUALS,
        IN,
        NOT_IN,
        TRUTHY,
        FALSY
    }

    public record JdbcUrlLinkage(
            String template, String hostField, String portField, String databaseField, boolean preserveSuffix) {
        public JdbcUrlLinkage {
            template = requireText(template, "JDBC URL template");
            hostField = normalize(hostField, "host");
            portField = normalize(portField, "port");
            databaseField = normalize(databaseField, "database");
        }
    }

    private static Set<String> immutableAliases(String type, Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String alias = normalizeType(value);
            if (!type.equals(alias)) normalized.add(alias);
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static Set<DataSourceCapability> immutableCapabilities(Set<DataSourceCapability> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copied.put(entry.getKey(), immutableValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(copied);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(DataSourcePluginDescriptor::immutableValue).toList();
        }
        return value;
    }

    private static String normalize(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private static String requireText(String value, String name) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
