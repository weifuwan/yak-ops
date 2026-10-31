package io.yak.ops.composer.validation;

import io.yak.ops.composer.definition.EndpointOptions;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.jdbc.database.schema.SchemaChangePlan;
import io.yak.ops.connector.jdbc.sink.JdbcMetadataApplier;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Schema constraints shared by publication, admission and execution planning. */
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

    /**
     * Enforces continuous MySQL row identity after the authorized Source table is resolved.
     * Snapshot Chunk Key is not a row identity; Sink write intent must be verified separately.
     */
    public static void validateCdcWrite(
            TableSchema sourceSchema,
            String targetType,
            ReadonlyConfig source,
            ReadonlyConfig sink,
            List<String> targetPrimaryKeys,
            boolean sourceProducesChangelog,
            boolean schemaEvolution) {
        if (!sourceProducesChangelog || !"mysql".equals(source.get(EndpointOptions.TYPE))) {
            return;
        }
        boolean noPrimaryKey = sourceSchema.primaryKeys().isEmpty();
        JdbcWriteMode mode = sink.get(JdbcSinkOptions.WRITE_MODE);
        if (noPrimaryKey) {
            if (!"MYSQL".equals(targetType)) {
                throw new IllegalArgumentException("CDC without physical primary keys requires a MySQL target");
            }
            if (schemaEvolution) {
                throw new IllegalArgumentException("CDC without physical primary keys cannot evolve target schema");
            }
            if (mode == JdbcWriteMode.UPSERT && targetPrimaryKeys == null) {
                throw new IllegalArgumentException(
                        "CDC without physical primary keys requires verified target keys for UPSERT");
            }
            if (mode != JdbcWriteMode.UPSERT && mode != JdbcWriteMode.RETRACT) {
                throw new IllegalArgumentException("Unsupported CDC write mode without physical primary keys");
            }
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
        SchemaChangePlan plan = SchemaPlanner.plan(desired, targetType, actual);
        requireCompatible(plan);
        if (plan.requiresDdl()) {
            throw new SchemaValidationException(
                    new IllegalArgumentException("Target structure still requires schema changes"));
        }
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
            for (Column column : actual.schema().columns()) {
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
