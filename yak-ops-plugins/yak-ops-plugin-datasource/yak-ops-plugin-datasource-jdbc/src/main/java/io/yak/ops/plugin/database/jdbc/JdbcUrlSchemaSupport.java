package io.yak.ops.plugin.database.jdbc;

import io.yak.ops.spi.datasource.DataSourcePluginDescriptor;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.ConnectionForm;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FieldType;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormField;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormSection;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.JdbcUrlLinkage;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.VisibilityCondition;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.VisibilityOperator;
import java.util.ArrayList;
import java.util.List;

/** Standard JDBC URL schema capability used by the shared frontend component. */
public final class JdbcUrlSchemaSupport {

    private static final String JDBC_URL_FIELD = "jdbcUrl";
    private static final String SSH_FIELD = "sshTunnel";
    private static final String SSH_ENABLED_FIELD = "sshTunnel.enabled";

    private JdbcUrlSchemaSupport() {}

    public static DataSourcePluginDescriptor apply(DataSourcePluginDescriptor descriptor, String template) {
        if (descriptor == null || template == null || template.trim().isEmpty()) {
            return descriptor;
        }

        ConnectionForm form = descriptor.connectionForm();
        List<FormSection> sections = form.sections().stream()
                .map(section -> section.withFields(configureFields(section.fields(), template)))
                .toList();
        List<FormField> legacyFields = configureFields(form.legacyFields(), template);

        return new DataSourcePluginDescriptor(
                descriptor.type(),
                descriptor.displayName(),
                descriptor.aliases(),
                descriptor.apiVersion(),
                descriptor.capabilities(),
                new ConnectionForm(sections, legacyFields));
    }

    private static List<FormField> configureFields(List<FormField> fields, String template) {
        if (fields == null || fields.isEmpty()) return List.of();
        return fields.stream().map(field -> configureField(field, template)).toList();
    }

    private static FormField configureField(FormField field, String template) {
        if (field == null || !JDBC_URL_FIELD.equals(field.key())) return field;

        List<String> dependencies = new ArrayList<>(field.dependsOn());
        if (!dependencies.contains(SSH_FIELD)) dependencies.add(SSH_FIELD);

        List<VisibilityCondition> conditions = new ArrayList<>(field.visibleWhen());
        boolean hasSshRule = conditions.stream()
                .anyMatch(condition -> condition != null
                        && SSH_ENABLED_FIELD.equals(condition.field())
                        && condition.operator() == VisibilityOperator.FALSY);
        if (!hasSshRule) {
            conditions.add(new VisibilityCondition(SSH_ENABLED_FIELD, VisibilityOperator.FALSY, null, List.of()));
        }

        return field.withType(FieldType.JDBC_URL)
                .withPlaceholder("根据主机、端口和数据库自动生成，也可以直接修改")
                .withJdbcUrlLinkage(new JdbcUrlLinkage(template.trim(), "host", "port", "database", true))
                .withDependsOn(dependencies)
                .withVisibleWhen(conditions);
    }
}
