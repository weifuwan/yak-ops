package io.yak.ops.composer.validation;

import io.yak.ops.composer.definition.EndpointOptions;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.definition.PipelineOptions;
import io.yak.ops.composer.definition.TableConfig;
import io.yak.ops.composer.execution.ExecutionPhase;
import io.yak.ops.composer.yakflow.translator.TransformTranslator;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.jdbc.database.schema.SchemaChangePlan;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.factories.DataSinkFactory;
import io.yak.ops.core.factories.DataSourceFactory;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskValidationException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Validates every explicit table mapping before publication or physical admission. */
public final class PipelineDefinitionValidator {
    private PipelineDefinitionValidator() {}

    public static PipelineOptions validateConfig(PipelineDef definition, FactoryUtil factories) {
        definition.getSource().getName().ifPresent(name -> {
            if (name.isBlank()) throw new IllegalArgumentException("Source name must not be blank");
        });
        definition.getSink().getName().ifPresent(name -> {
            if (name.isBlank()) throw new IllegalArgumentException("Sink name must not be blank");
        });
        new TransformTranslator(definition);
        return validateConfig(PipelineOptions.from(definition), factories);
    }

    private static PipelineOptions validateConfig(PipelineOptions job, FactoryUtil factories) {
        Objects.requireNonNull(factories, "factories");
        try {
            for (TableConfig table : job.tables()) {
                DataSourceFactory sourceFactory =
                        factories.source(table.source().get(EndpointOptions.TYPE));
                DataSinkFactory sinkFactory = factories.sink(table.sink().get(EndpointOptions.TYPE));
                FactoryUtil.validate(table.source(), sourceFactory, EndpointOptions.ENDPOINT_RULE);
                FactoryUtil.validate(table.sink(), sinkFactory, EndpointOptions.ENDPOINT_RULE);
                sinkFactory.validate(
                        table.sink(),
                        sourceFactory.producesChangelog(EndpointOptions.connectorOptions(table.source())));
                Boundedness boundedness = sourceFactory.boundedness(EndpointOptions.connectorOptions(table.source()));
                if (job.bounded() != (boundedness == Boundedness.BOUNDED)) {
                    throw new IllegalArgumentException("Pipeline runtime mode conflicts with Source boundedness");
                }
            }
            return job;
        } catch (RuntimeException failure) {
            throw new TaskPluginException("Invalid DATA_SYNC configuration", failure);
        }
    }

    /** Resolves authorized namespaces and rejects aliases of duplicate tables without opening tables. */
    public static void validateReferences(
            PipelineDef definition, TaskValidationContext context, FactoryUtil factories) {
        validateBindings(definition, PipelineOptions.from(definition), context, factories, false);
    }

    /** Validates all authorized source/target schemas; no DDL or writes are performed. */
    public static void validateResources(PipelineDef definition, TaskValidationContext context, FactoryUtil factories) {
        validateBindings(definition, PipelineOptions.from(definition), context, factories, true);
    }

    private static void validateBindings(
            PipelineDef definition,
            PipelineOptions job,
            TaskValidationContext context,
            FactoryUtil factories,
            boolean schemas) {
        try {
            String sourceId = job.sourceDataSourceId();
            String sinkId = job.targetDataSourceId();
            DataSourceInfo from = context.dataSource(sourceId);
            DataSourceInfo to = context.dataSource(sinkId);
            boolean prepareSchema = job.steps().contains(ExecutionPhase.SCHEMA);
            Set<TableId> sources = new HashSet<>();
            Set<TableId> targets = new HashSet<>();
            for (TableConfig table : job.tables()) {
                DataSourceFactory sourceFactory =
                        factories.source(table.source().get(EndpointOptions.TYPE));
                DataSinkFactory sinkFactory = factories.sink(table.sink().get(EndpointOptions.TYPE));
                sinkFactory.validateDatasource(to.type());
                validateCapabilities(job, sourceFactory, sinkFactory);
                sourceFactory.validateDatasource(from.type());
                TableId source = tablePath(from, table.source());
                TableId target = tablePath(to, table.sink());
                if (!sources.add(source) || !targets.add(target)) {
                    throw new IllegalArgumentException("Duplicate resolved source or destination table");
                }
                if (table.targetPrimaryKeys() != null && !"MYSQL".equals(to.type())) {
                    throw new IllegalArgumentException("Custom target primary keys currently require MySQL targets");
                }
                if (prepareSchema) SchemaValidator.validateNamespace(to, target);
                if (schemas && prepareSchema) {
                    DataSourceTable sourceTable =
                            SchemaValidator.sourceMetadata(() -> context.tableMetadata(sourceId, source));
                    SchemaValidator.validateCdcWrite(
                            sourceTable.schema(),
                            to.type(),
                            table.source(),
                            table.sink(),
                            table.targetPrimaryKeys(),
                            sourceFactory.producesChangelog(EndpointOptions.connectorOptions(table.source())),
                            job.schemaChangesEnabled());
                    DataSourceTable projected = project(definition, sourceTable, table.source(), table.sink());
                    TableSchema writeSource = SchemaValidator.writeSchema(projected, table.targetPrimaryKeys());
                    SchemaValidator.validateWrite(() -> sourceFactory.validateSchema(
                            EndpointOptions.connectorOptions(table.source()), sourceTable.schema()));
                    CreateTableEvent event =
                            SchemaPlanner.createEvent(projected, to, target, table.targetPrimaryKeys());
                    DataSourceTable current = context.findTable(sinkId, target).orElse(null);
                    if (current != null && current.revision() != to.revision()) {
                        throw new IllegalStateException("Target datasource revision changed during validation");
                    }
                    SchemaChangePlan schemaPlan = SchemaPlanner.plan(event, to.type(), current);
                    SchemaValidator.requireCompatible(schemaPlan);
                    SchemaValidator.validateWrite(() -> sinkFactory.validateSchema(
                            table.sink(),
                            writeSource,
                            event.getSchema(),
                            sourceFactory.producesChangelog(EndpointOptions.connectorOptions(table.source()))));
                    if (current != null && !schemaPlan.requiresDdl()) {
                        SchemaValidator.validateWrite(() -> sinkFactory.validateSchema(
                                table.sink(),
                                writeSource,
                                current.schema(),
                                sourceFactory.producesChangelog(EndpointOptions.connectorOptions(table.source()))));
                    }
                } else if (schemas) {
                    TableSchema sourceSchema = context.tableSchema(sourceId, source);
                    SchemaValidator.validateCdcWrite(
                            sourceSchema,
                            to.type(),
                            table.source(),
                            table.sink(),
                            table.targetPrimaryKeys(),
                            sourceFactory.producesChangelog(EndpointOptions.connectorOptions(table.source())),
                            job.schemaChangesEnabled());
                    TableSchema writeSource = SchemaValidator.writeSchema(
                            project(definition, context.tableMetadata(sourceId, source), table.source(), table.sink()),
                            table.targetPrimaryKeys());
                    SchemaValidator.validateWrite(() -> sourceFactory.validateSchema(
                            EndpointOptions.connectorOptions(table.source()), sourceSchema));
                    TableSchema targetSchema = context.tableSchema(sinkId, target);
                    SchemaValidator.validateWrite(() -> sinkFactory.validateSchema(
                            table.sink(),
                            writeSource,
                            targetSchema,
                            sourceFactory.producesChangelog(EndpointOptions.connectorOptions(table.source()))));
                }
            }
            if (sourceId.equals(sinkId) && sources.stream().anyMatch(targets::contains)) {
                throw new IllegalArgumentException("Batch destinations overlap their source tables");
            }
        } catch (RuntimeException failure) {
            if (schemas && failure instanceof SchemaValidationException schemaFailure) {
                throw new TaskValidationException(
                        "SCHEMA_VALIDATION",
                        SchemaValidationMessages.safeSchemaMessage(schemaFailure.failure()),
                        schemaFailure);
            }
            if (schemas) {
                String message = SchemaValidationMessages.safeSchemaMessage(failure);
                if (!message.equals("目标表结构不兼容，请检查字段类型和默认值等属性")) {
                    throw new TaskValidationException("SCHEMA_VALIDATION", message, failure);
                }
            }
            throw new TaskPluginException("Invalid DATA_SYNC resource binding or Schema", failure);
        }
    }

    private static DataSourceTable project(
            PipelineDef definition, DataSourceTable table, ReadonlyConfig source, ReadonlyConfig sink) {
        TransformTranslator transforms = new TransformTranslator(
                definition, java.util.Map.of(table.table(), EndpointOptions.selectedTable(source)));
        if (sink.get(JdbcSinkOptions.WRITE_MODE) == JdbcWriteMode.RETRACT) {
            transforms.validateRetraction(table.table());
        }
        return transforms.transformMetadata(table);
    }

    /** Validates platform features against the actual endpoint capabilities. */
    public static void validateCapabilities(PipelineOptions options, DataSourceFactory source, DataSinkFactory sink) {
        if (options.prepareSchema() && (!source.supportsSchemaMetadata() || !sink.supportsSchemaChanges())) {
            throw new IllegalArgumentException("Initial schema preparation requires metadata-capable endpoints");
        }
        if (options.schemaChangesEnabled() && (!source.producesChangelog() || !sink.supportsSchemaChanges())) {
            throw new IllegalArgumentException(
                    "Schema evolution requires compatible Source and Sink metadata capabilities");
        }
        if (options.bounded() && options.incremental() && !source.supportsIncrementalBatch()) {
            throw new IllegalArgumentException("Incremental batch requires a cursor-capable Source");
        }
    }

    /** Resolves explicitly selected namespace values without changing the persisted configuration. */
    public static TableId tablePath(DataSourceInfo info, ReadonlyConfig options) {
        return info.resolveTable(
                options.get(EndpointOptions.DATABASE),
                options.get(EndpointOptions.SCHEMA),
                options.get(EndpointOptions.TABLE));
    }
}
