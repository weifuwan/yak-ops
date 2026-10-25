package io.yak.ops.plugin.task.datasync.validation;

import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDdlPlan;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;
import io.yak.ops.connector.jdbc.database.schema.JdbcSchemaDerivator;
import io.yak.ops.connector.jdbc.database.schema.SchemaChangePlan;
import io.yak.ops.connector.jdbc.sink.JdbcMetadataApplier;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.AlterColumnTypeEvent;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read-only constraints shared by schema preview, publication and execution planning. */
public final class SchemaValidator {
    private SchemaValidator() {}

    /**
     * WRITE validation checks only the JDBC write contract. It must never demand
     * identical defaults, comments, indexes, or a source-derived target DDL.
     */
    public static void validateWrite(Runnable validation) {
        try {
            validation.run();
        } catch (IllegalArgumentException | UnsupportedOperationException failure) {
            throw new SchemaValidationException(failure);
        }
    }

    /** Classifies schema source-metadata failures separately from connection failures. */
    public static DataSourceTable sourceMetadata(java.util.function.Supplier<DataSourceTable> lookup) {
        try {
            return lookup.get();
        } catch (IllegalArgumentException failure) {
            throw new SchemaValidationException(failure);
        }
    }

    /** Maps an independently verified physical unique key to the downstream logical Schema. */
    public static TableSchema writeSchema(DataSourceTable source, List<String> targetPrimaryKeys) {
        if (targetPrimaryKeys == null) return source.schema();
        if (!"MYSQL".equals(source.datasourceType()) || targetPrimaryKeys.isEmpty()) {
            throw new IllegalArgumentException("Explicit target primary keys require MySQL source metadata");
        }
        Set<String> selected = new HashSet<>(targetPrimaryKeys);
        boolean hasUniqueIndex = source.uniqueKeys().stream()
                .anyMatch(index -> index.size() == selected.size() && selected.containsAll(index));
        boolean matchesSourcePrimaryKey = source.schema().primaryKeys().size() == selected.size()
                && selected.containsAll(source.schema().primaryKeys());
        if (selected.size() != targetPrimaryKeys.size() || (!hasUniqueIndex && !matchesSourcePrimaryKey)) {
            throw new IllegalArgumentException("Target primary key must match an enforced source unique index");
        }
        for (String key : targetPrimaryKeys) {
            Column column = source.schema().columns().stream()
                    .filter(candidate -> candidate.name().equals(key))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown target key column"));
            if (column.nullable()) throw new IllegalArgumentException("Target primary key cannot be nullable: " + key);
            // Until collation and floating-point comparison are proved, accept exact scalar keys only.
            switch (column.dataType().getTypeRoot()) {
                case TINYINT, SMALLINT, INTEGER, BIGINT, DECIMAL, BINARY, VARBINARY -> {}
                default -> throw new IllegalArgumentException("Unverified target primary-key comparison: " + key);
            }
        }
        return new TableSchema(source.schema().columns(), targetPrimaryKeys);
    }

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
            validateNamespace(target, table);
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
            var properties = source.columnProperties();
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
                    writeSchema(source, primaryKeys),
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
     * Requires explicit target namespaces without parsing or quoting user-supplied SQL paths.
     *
     * @param target authorized target datasource
     * @param table resolved target table
     */
    public static void validateNamespace(DataSourceInfo target, TableId table) {
        if (table.catalog() == null
                || ("POSTGRE_SQL".equals(target.type()) && table.schema() == null)
                || ("MYSQL".equals(target.type()) && table.schema() != null)) {
            throw new IllegalArgumentException("Schema preparation requires an explicit target namespace");
        }
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

    /** Refuses partial schema migration plans before the task can be published or admitted. */
    public static void requireCompatible(SchemaChangePlan plan) {
        if (plan.blocked()) {
            String reason = plan.differences().stream()
                    .filter(difference -> difference.kind() == SchemaChangePlan.Kind.INCOMPATIBLE)
                    .map(difference -> difference.columnName() == null
                            ? difference.reason().name()
                            : difference.reason().name() + ": " + difference.columnName())
                    .findFirst()
                    .orElse("UNKNOWN");
            throw new SchemaValidationException(new IllegalArgumentException("Schema change blocked: " + reason));
        }
    }

    /** Re-reads the physical target and requires all planned changes to be complete. */
    public static void validatePrepared(CreateTableEvent desired, String targetType, DataSourceTable actual) {
        SchemaChangePlan plan = plan(desired, targetType, actual);
        requireCompatible(plan);
        if (plan.requiresDdl()) {
            throw new SchemaValidationException(
                    new IllegalArgumentException("Target structure still requires schema changes"));
        }
    }

    /** Read-only DDL rendering from the exact supported events dispatched to MetadataApplier. */
    public static List<String> statements(String targetType, SchemaChangePlan plan, DataSourceTable actual) {
        requireCompatible(plan);
        JdbcDialect dialect = dialect(targetType);
        List<String> statements = new ArrayList<>();
        for (SchemaChangeEvent event : plan.changes()) {
            if (event instanceof CreateTableEvent create) {
                statements.addAll(JdbcMetadataApplier.plan(create, dialect).statements());
            } else if (event instanceof AddColumnEvent add) {
                for (AddColumnEvent.ColumnWithPosition column : add.addedColumns()) {
                    statements.addAll(dialect.addColumnStatements(event.tableId(), column.column()));
                }
            } else if (event instanceof AlterColumnTypeEvent alter) {
                for (String name :
                        alter.typeMapping().keySet().stream().sorted().toList()) {
                    Column before = new Column(name, alter.oldTypeMapping().get(name));
                    Column after = new Column(name, alter.typeMapping().get(name));
                    String comment =
                            actual == null ? null : actual.columnComments().get(name);
                    statements.add(dialect.alterColumnTypeSql(event.tableId(), before, after, comment));
                }
            } else {
                throw new SchemaValidationException(new IllegalArgumentException("Unsupported planned schema event"));
            }
        }
        return List.copyOf(statements);
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

    /**
     * Checks an existing table without modifying it or accepting an incomplete write projection.
     *
     * @param event complete expected structure
     * @param actual current physical target metadata
     */
    public static void validateTarget(CreateTableEvent event, DataSourceTable actual) {
        try {
            if (!event.tableId().equals(actual.table())
                    || actual.columnFeatures().contains("NON_TABLE")
                    || actual.columnFeatures().contains("UNKNOWN_GENERATION")) {
                throw new IllegalArgumentException("Target physical identity or column features are incompatible");
            }
            JdbcMetadataApplier.validateSchema(event, actual.schema());
            for (var column : actual.schema().columns()) {
                ColumnProperties properties = actual.columnProperties().get(column.name());
                if (properties == null) {
                    throw new IllegalArgumentException("Target column properties unavailable: " + column.name());
                }
                JdbcMetadataApplier.validateColumnProperties(
                        column.name(), event.getColumnProperties().get(column.name()), properties);
            }
            JdbcMetadataApplier.validateComments(event, actual.tableComment(), actual.columnComments());
        } catch (IllegalArgumentException | UnsupportedOperationException failure) {
            throw new SchemaValidationException(failure);
        }
    }
}
