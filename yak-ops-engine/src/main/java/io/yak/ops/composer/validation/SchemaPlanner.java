package io.yak.ops.composer.validation;

import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDdlPlan;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;
import io.yak.ops.connector.jdbc.database.schema.JdbcSchemaDerivator;
import io.yak.ops.connector.jdbc.database.schema.SchemaChangePlan;
import io.yak.ops.connector.jdbc.sink.JdbcMetadataApplier;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AlterColumnTypeEvent;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds target creation events and dialect-validated SchemaChangePlans without executing DDL. */
public final class SchemaPlanner {
    private SchemaPlanner() {}

    /**
     * Builds a complete desired target event from an authorized source table.
     *
     * @param source physical source metadata
     * @param target authorized target datasource and revision
     * @param table resolved target identity
     * @return validated target creation event
     */
    public static CreateTableEvent createEvent(DataSourceTable source, DataSourceInfo target, TableId table) {
        return createEvent(source, target, table, null);
    }

    public static CreateTableEvent createEvent(
            DataSourceTable source, DataSourceInfo target, TableId table, List<String> primaryKeys) {
        try {
            SchemaValidator.validateNamespace(target, table);
            if (source.columnFeatures().contains("NON_TABLE")) {
                throw new IllegalArgumentException("Schema preparation requires a physical source table");
            }
            if (source.columnFeatures().contains("UNKNOWN_GENERATION")) {
                throw new IllegalArgumentException("Source column generation metadata is incomplete");
            }
            if (!source.columnFeatures().isEmpty() && source.columnProperties().isEmpty()) {
                throw new IllegalArgumentException("Source physical column properties are unavailable");
            }
            boolean hasNativeFeatures = source.columnProperties().values().stream()
                    .anyMatch(properties -> properties.hasFeatures() || requiresSameVendor(properties.nativeType()));
            if (hasNativeFeatures && !source.datasourceType().equals(target.type())) {
                throw new IllegalArgumentException(
                        "Physical column expressions require a verified same-database dialect");
            }
            java.util.Map<String, ColumnProperties> properties = source.columnProperties();
            if (!source.datasourceType().equals(target.type())) {
                // MySQL COLUMN_TYPE belongs to its source dialect; other targets use logical types.
                properties = properties.entrySet().stream()
                        .collect(java.util.stream.Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey, entry -> {
                            ColumnProperties sourceProperties = entry.getValue();
                            return new ColumnProperties(
                                    sourceProperties.defaultExpression(),
                                    sourceProperties.autoIncrement(),
                                    sourceProperties.generated(),
                                    sourceProperties.onUpdateExpression(),
                                    sourceProperties.defaultGenerated(),
                                    null);
                        }));
            }
            CreateTableEvent event = new CreateTableEvent(
                    table,
                    SchemaValidator.writeSchema(source, primaryKeys),
                    source.tableComment(),
                    source.columnComments(),
                    properties);
            return event;
        } catch (IllegalArgumentException | UnsupportedOperationException failure) {
            throw new SchemaValidationException(failure);
        }
    }

    private static boolean requiresSameVendor(String nativeType) {
        return nativeType != null
                && nativeType
                        .toLowerCase(java.util.Locale.ROOT)
                        .matches(".*\\b(enum|set|json|unsigned|zerofill|year)\\b.*");
    }

    /**
     * Renders the execution DDL using only the resolved vendor type and expected event.
     *
     * @param targetType resolved datasource type
     * @param event complete expected target structure
     * @return validated ordered statements; no driver or database is accessed
     */
    public static JdbcDdlPlan ddl(String targetType, CreateTableEvent event) {
        String prefix =
                switch (targetType) {
                    case "MYSQL" -> "jdbc:mysql:";
                    case "POSTGRE_SQL" -> "jdbc:postgresql:";
                    default -> throw new IllegalArgumentException("Schema preparation target is not supported");
                };
        return JdbcMetadataApplier.plan(event, JdbcFactoryLoader.loadDialect(prefix));
    }

    /**
     * Uses the same dialect and physical metadata for publication, preview and execution planning.
     * Existing targets are compared structurally; an absent target retains the source's
     * original CREATE_TABLE event and its validated native attributes.
     */
    public static SchemaChangePlan plan(CreateTableEvent desired, String targetType, DataSourceTable actual) {
        try {
            JdbcDialect dialect = dialect(targetType);
            if (actual == null) {
                ddl(targetType, desired);
                return new SchemaChangePlan(
                        desired.tableId(),
                        List.of(new SchemaChangePlan.Difference(
                                null,
                                SchemaChangePlan.Kind.CREATE_TABLE,
                                SchemaChangePlan.Reason.TABLE_MISSING,
                                null,
                                null)),
                        List.of(desired));
            }
            if (!desired.tableId().equals(actual.table())
                    || actual.columnFeatures().contains("NON_TABLE")
                    || actual.columnFeatures().contains("UNKNOWN_GENERATION")) {
                throw new IllegalArgumentException("Target physical identity or column features are incompatible");
            }
            Schema existing = Schema.from(
                    actual.schema(), actual.tableComment(), actual.columnComments(), actual.columnProperties());
            SchemaChangePlan derived = JdbcSchemaDerivator.derive(
                    desired.tableId(), desired.getPhysicalSchema(), Optional.of(existing), dialect);
            return checkDdlCapabilities(derived, actual, dialect);
        } catch (IllegalArgumentException | UnsupportedOperationException failure) {
            throw new SchemaValidationException(failure);
        }
    }

    /**
     * A structurally safe type change is not executable if the physical target has defaults,
     * generated values, or other attributes that the JDBC DDL would inadvertently remove.
     * A blocked plan cannot contain partially executable statements.
     */
    private static SchemaChangePlan checkDdlCapabilities(
            SchemaChangePlan plan, DataSourceTable actual, JdbcDialect dialect) {
        if (plan.blocked()) return plan;
        List<SchemaChangePlan.Difference> issues = new ArrayList<>();
        for (SchemaChangeEvent event : plan.changes()) {
            if (event instanceof AlterColumnTypeEvent alter) {
                for (String name : alter.typeMapping().keySet()) {
                    ColumnProperties properties = actual.columnProperties().get(name);
                    if (properties == null
                            || !properties.isKnown()
                            || properties.hasFeatures()
                            || (properties.nativeType() != null
                                    && MySqlJdbcDialect.requiresExactNativeType(properties.nativeType()))) {
                        issues.add(new SchemaChangePlan.Difference(
                                name,
                                SchemaChangePlan.Kind.INCOMPATIBLE,
                                SchemaChangePlan.Reason.UNSAFE_COLUMN_PROPERTIES,
                                alter.typeMapping().get(name),
                                alter.oldTypeMapping().get(name)));
                    }
                }
            }
        }
        if (issues.isEmpty()) return plan;
        List<SchemaChangePlan.Difference> differences = new ArrayList<>(plan.differences());
        differences.addAll(issues);
        return new SchemaChangePlan(plan.tableId(), differences, List.of());
    }

    /** Resolves vendor dialect without opening a connection or accepting arbitrary SQL URLs. */
    private static JdbcDialect dialect(String targetType) {
        String prefix =
                switch (targetType) {
                    case "MYSQL" -> "jdbc:mysql:";
                    case "POSTGRE_SQL" -> "jdbc:postgresql:";
                    default -> throw new IllegalArgumentException("Schema preparation target is not supported");
                };
        return JdbcFactoryLoader.loadDialect(prefix);
    }
}
