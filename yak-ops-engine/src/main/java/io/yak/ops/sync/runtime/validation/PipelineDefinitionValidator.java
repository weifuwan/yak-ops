package io.yak.ops.sync.runtime.validation;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskValidationException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.sync.runtime.config.JobOptions;
import io.yak.ops.sync.runtime.config.PipelineExecutionPlan;
import io.yak.ops.sync.runtime.solution.SolutionValidator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Validates every explicit table mapping before publication or physical admission. */
public final class PipelineDefinitionValidator {
    private PipelineDefinitionValidator() {}

    public static PipelineExecutionPlan validateConfig(PipelineDef definition, FactoryUtil factories) {
        return validateConfig(PipelineExecutionPlan.from(definition), factories);
    }

    private static PipelineExecutionPlan validateConfig(PipelineExecutionPlan job, FactoryUtil factories) {
        Objects.requireNonNull(factories, "factories");
        try {
            for (io.yak.ops.sync.runtime.config.TableConfig table : job.tables()) {
                io.yak.ops.connector.base.factory.TableSourceFactory sourceFactory =
                        factories.source(table.source().get(JobOptions.TYPE));
                io.yak.ops.connector.base.factory.TableSinkFactory sinkFactory =
                        factories.sink(table.sink().get(JobOptions.TYPE));
                FactoryUtil.validate(table.source(), sourceFactory, JobOptions.ENDPOINT_RULE);
                FactoryUtil.validate(table.sink(), sinkFactory, JobOptions.ENDPOINT_RULE);
                sinkFactory.validate(table.sink(), sourceFactory.producesChangelog());
            }
            return job;
        } catch (RuntimeException failure) {
            throw new TaskPluginException("Invalid DATA_SYNC configuration", failure);
        }
    }

    /** Resolves authorized namespaces and rejects aliases of duplicate tables without opening tables. */
    public static void validateReferences(
            PipelineDef definition, TaskValidationContext context, FactoryUtil factories) {
        validateBindings(PipelineExecutionPlan.from(definition), context, factories, false);
    }

    /** Validates all authorized source/target schemas; no DDL or writes are performed. */
    public static void validateResources(PipelineDef definition, TaskValidationContext context, FactoryUtil factories) {
        validateBindings(PipelineExecutionPlan.from(definition), context, factories, true);
    }

    private static void validateBindings(
            PipelineExecutionPlan job, TaskValidationContext context, FactoryUtil factories, boolean schemas) {
        try {
            String sourceId = job.sourceDataSourceId();
            String sinkId = job.targetDataSourceId();
            DataSourceInfo from = context.dataSource(sourceId);
            DataSourceInfo to = context.dataSource(sinkId);
            boolean prepareSchema = job.steps().contains(io.yak.ops.sync.runtime.solution.Step.SCHEMA);
            Set<TableId> sources = new HashSet<>();
            Set<TableId> targets = new HashSet<>();
            for (io.yak.ops.sync.runtime.config.TableConfig table : job.tables()) {
                io.yak.ops.connector.base.factory.TableSourceFactory sourceFactory =
                        factories.source(table.source().get(JobOptions.TYPE));
                io.yak.ops.connector.base.factory.TableSinkFactory sinkFactory =
                        factories.sink(table.sink().get(JobOptions.TYPE));
                SolutionValidator.validateSelection(
                        factories,
                        job.solutionType(),
                        from.type(),
                        to.type(),
                        job.steps(),
                        job.schemaChangesEnabled(),
                        table.sink().get(JdbcSinkOptions.WRITE_MODE).name());
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
                    io.yak.ops.plugin.datasource.api.lookup.DataSourceTable sourceTable =
                            SchemaValidator.sourceMetadata(() -> context.tableMetadata(sourceId, source));
                    SchemaValidator.validateCdcWrite(
                            sourceTable.schema(),
                            to.type(),
                            table.source(),
                            table.sink(),
                            table.targetPrimaryKeys(),
                            job.schemaChangesEnabled());
                    io.yak.ops.core.types.TableSchema writeSource =
                            SchemaValidator.writeSchema(sourceTable, table.targetPrimaryKeys());
                    SchemaValidator.validateWrite(() -> sourceFactory.validateSchema(
                            JobOptions.connectorOptions(table.source()), sourceTable.schema()));
                    io.yak.ops.core.event.CreateTableEvent event =
                            SchemaPlanner.createEvent(sourceTable, to, target, table.targetPrimaryKeys());
                    io.yak.ops.plugin.datasource.api.lookup.DataSourceTable current =
                            context.findTable(sinkId, target).orElse(null);
                    if (current != null && current.revision() != to.revision()) {
                        throw new IllegalStateException("Target datasource revision changed during validation");
                    }
                    io.yak.ops.connector.jdbc.database.schema.SchemaChangePlan schemaPlan =
                            SchemaPlanner.plan(event, to.type(), current);
                    SchemaValidator.requireCompatible(schemaPlan);
                    SchemaValidator.validateWrite(() -> sinkFactory.validateSchema(
                            table.sink(), writeSource, event.getSchema(), sourceFactory.producesChangelog()));
                    if (current != null && !schemaPlan.requiresDdl()) {
                        SchemaValidator.validateWrite(() -> sinkFactory.validateSchema(
                                table.sink(), writeSource, current.schema(), sourceFactory.producesChangelog()));
                    }
                } else if (schemas) {
                    io.yak.ops.core.types.TableSchema sourceSchema = context.tableSchema(sourceId, source);
                    SchemaValidator.validateCdcWrite(
                            sourceSchema,
                            to.type(),
                            table.source(),
                            table.sink(),
                            table.targetPrimaryKeys(),
                            job.schemaChangesEnabled());
                    io.yak.ops.core.types.TableSchema writeSource = table.targetPrimaryKeys() == null
                            ? sourceSchema
                            : SchemaValidator.writeSchema(
                                    context.tableMetadata(sourceId, source), table.targetPrimaryKeys());
                    SchemaValidator.validateWrite(() ->
                            sourceFactory.validateSchema(JobOptions.connectorOptions(table.source()), sourceSchema));
                    io.yak.ops.core.types.TableSchema targetSchema = context.tableSchema(sinkId, target);
                    SchemaValidator.validateWrite(() -> sinkFactory.validateSchema(
                            table.sink(), writeSource, targetSchema, sourceFactory.producesChangelog()));
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

    /** Resolves explicitly selected namespace values without changing the persisted configuration. */
    public static TableId tablePath(DataSourceInfo info, ReadonlyConfig options) {
        return info.resolveTable(
                options.get(JobOptions.DATABASE), options.get(JobOptions.SCHEMA), options.get(JobOptions.TABLE));
    }
}
