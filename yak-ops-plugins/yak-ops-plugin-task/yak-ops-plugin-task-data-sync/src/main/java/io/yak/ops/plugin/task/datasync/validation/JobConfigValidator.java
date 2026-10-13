package io.yak.ops.plugin.task.datasync.validation;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.config.JobConfig;
import io.yak.ops.plugin.task.datasync.config.JobOptions;
import java.util.Objects;

/**
 * Validates pipeline structure and authorized table metadata.
 *
 * <p>Connector Factory option rules are authoritative for vendor-specific fields. Metadata
 * lookup does not resolve credentials, mutate the selected namespace, or create jobs.
 */
public final class JobConfigValidator {

    private JobConfigValidator() {}

    /** Checks task, endpoint, and Connector static rules without external access. */
    public static JobConfig validateConfig(ReadonlyConfig parameters, FactoryUtil factories) {
        Objects.requireNonNull(factories, "factories");
        try {
            ConfigValidator.of(parameters).validate(JobOptions.PIPELINE_RULE);
            ConfigValidator.validateUnknownKeys(parameters, JobOptions.PIPELINE_RULE, "DATA_SYNC");
            JobConfig job = JobConfig.from(parameters);
            var sourceFactory = factories.source(job.source().get(JobOptions.TYPE));
            var sinkFactory = factories.sink(job.sink().get(JobOptions.TYPE));
            FactoryUtil.validate(job.source(), sourceFactory, JobOptions.ENDPOINT_RULE);
            FactoryUtil.validate(job.sink(), sinkFactory, JobOptions.ENDPOINT_RULE);
            sinkFactory.validate(job.sink(), sourceFactory.producesChangelog());
            return job;
        } catch (RuntimeException failure) {
            throw new TaskPluginException("Invalid DATA_SYNC configuration", failure);
        }
    }

    /**
     * Checks authorized Datasource identities and explicit namespace bindings without
     * opening tables or reading schema metadata.
     */
    public static void validateReferences(
            ReadonlyConfig parameters, TaskValidationContext context, FactoryUtil factories) {
        JobConfig job = validateConfig(parameters, factories);
        try {
            DataSourceInfo sourceInfo = context.dataSource(job.source().get(JobOptions.DATASOURCE_ID));
            DataSourceInfo sinkInfo = context.dataSource(job.sink().get(JobOptions.DATASOURCE_ID));
            factories.source(job.source().get(JobOptions.TYPE)).validateDatasource(sourceInfo.type());
            tablePath(sourceInfo, job.source());
            tablePath(sinkInfo, job.sink());
        } catch (RuntimeException failure) {
            throw new TaskPluginException("Invalid DATA_SYNC Datasource reference", failure);
        }
    }

    /** Checks authorized resource bindings and executable source-to-target Schema. */
    public static void validateResources(
            ReadonlyConfig parameters, TaskValidationContext context, FactoryUtil factories) {
        JobConfig job = validateConfig(parameters, factories);
        try {
            String sourceId = job.source().get(JobOptions.DATASOURCE_ID);
            String sinkId = job.sink().get(JobOptions.DATASOURCE_ID);
            DataSourceInfo sourceInfo = context.dataSource(sourceId);
            DataSourceInfo sinkInfo = context.dataSource(sinkId);
            var sourceFactory = factories.source(job.source().get(JobOptions.TYPE));
            var sinkFactory = factories.sink(job.sink().get(JobOptions.TYPE));
            sourceFactory.validateDatasource(sourceInfo.type());
            TableId sourceTable = tablePath(sourceInfo, job.source());
            TableId sinkTable = tablePath(sinkInfo, job.sink());
            TableSchema sourceSchema = context.tableSchema(sourceId, sourceTable);
            TableSchema sinkSchema = context.tableSchema(sinkId, sinkTable);
            sinkFactory.validateSchema(job.sink(), sourceSchema, sinkSchema, sourceFactory.producesChangelog());
        } catch (RuntimeException failure) {
            throw new TaskPluginException("Invalid DATA_SYNC resource binding or Schema", failure);
        }
    }

    /** Uses explicitly selected namespace values without changing saved configuration. */
    public static TableId tablePath(DataSourceInfo info, ReadonlyConfig options) {
        return info.resolveTable(
                options.get(JobOptions.DATABASE), options.get(JobOptions.SCHEMA), options.get(JobOptions.TABLE));
    }
}
