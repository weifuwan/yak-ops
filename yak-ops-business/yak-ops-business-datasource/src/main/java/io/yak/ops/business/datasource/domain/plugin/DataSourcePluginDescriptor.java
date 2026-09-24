package io.yak.ops.business.datasource.domain.plugin;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Business-owned projection of one datasource plugin descriptor. */
public record DataSourcePluginDescriptor(
        DataSourceDbType dbType,
        String displayName,
        String apiVersion,
        Set<Capability> capabilities,
        List<FormSection> sections,
        List<FormField> legacyFields,
        boolean installRequired,
        String installHint) {

    public DataSourcePluginDescriptor {
        dbType = Objects.requireNonNull(dbType, "dbType");
        displayName = requireText(displayName, "displayName");
        apiVersion = requireText(apiVersion, "apiVersion");
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        sections = sections == null ? List.of() : List.copyOf(sections);
        legacyFields = legacyFields == null ? List.of() : List.copyOf(legacyFields);
    }

    public boolean supports(Capability capability) {
        return capability != null && capabilities.contains(capability);
    }

    public enum Capability {
        CONNECTION_TEST,
        CATALOG_METADATA,
        CATALOG_READ,
        SQL_EXECUTION,
        TRANSACTIONS,
        SSH_TUNNEL
    }

    public record FormSection(
            String key,
            String title,
            String description,
            boolean collapsible,
            boolean defaultExpanded,
            List<FormField> fields) {
        public FormSection {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    public record FormField(
            String key,
            String label,
            String type,
            String placeholder,
            Object defaultValue,
            List<FormOption> options,
            List<FormRule> rules,
            List<String> dependsOn,
            List<VisibilityCondition> visibleWhen,
            JdbcUrlLinkage jdbcUrlLinkage) {
        public FormField {
            options = options == null ? List.of() : List.copyOf(options);
            rules = rules == null ? List.of() : List.copyOf(rules);
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
            visibleWhen = visibleWhen == null ? List.of() : List.copyOf(visibleWhen);
        }
    }

    public record FormOption(String label, Object value) {}

    public record FormRule(Boolean required, String pattern, Integer min, Integer max, String message) {}

    public record VisibilityCondition(String field, String operator, Object value, List<Object> values) {
        public VisibilityCondition {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record JdbcUrlLinkage(
            String template, String hostField, String portField, String databaseField, boolean preserveSuffix) {}

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
