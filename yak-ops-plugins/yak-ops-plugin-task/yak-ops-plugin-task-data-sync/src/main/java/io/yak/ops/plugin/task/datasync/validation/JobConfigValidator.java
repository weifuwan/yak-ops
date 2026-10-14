package io.yak.ops.plugin.task.datasync.validation;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.data.TableId;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.config.JobConfig;
import io.yak.ops.plugin.task.datasync.config.JobOptions;
import io.yak.ops.plugin.task.datasync.solution.SolutionCatalog;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Validates every explicit table mapping before publication or physical admission. */
public final class JobConfigValidator {
    private JobConfigValidator() {}

    /** Checks all Connector options without external access or connection credentials. */
    public static JobConfig validateConfig(ReadonlyConfig parameters, FactoryUtil factories) {
        Objects.requireNonNull(factories, "factories");
        try {
            JobConfig job = JobConfig.from(parameters);
            for (var table : job.tables()) {
                var sourceFactory = factories.source(table.source().get(JobOptions.TYPE));
                var sinkFactory = factories.sink(table.sink().get(JobOptions.TYPE));
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
            ReadonlyConfig parameters, TaskValidationContext context, FactoryUtil factories) {
        validateBindings(parameters, context, factories, false);
    }

    /** Validates all authorized source/target schemas; no DDL or writes are performed. */
    public static void validateResources(
            ReadonlyConfig parameters, TaskValidationContext context, FactoryUtil factories) {
        validateBindings(parameters, context, factories, true);
    }

    private static void validateBindings(
            ReadonlyConfig parameters, TaskValidationContext context, FactoryUtil factories, boolean schemas) {
        JobConfig job = validateConfig(parameters, factories);
        try {
            String sourceId = job.sourceDataSourceId();
            String sinkId = job.targetDataSourceId();
            DataSourceInfo from = context.dataSource(sourceId);
            DataSourceInfo to = context.dataSource(sinkId);
            SolutionCatalog.validate(job.solutionType(), from.type(), to.type());
            Set<TableId> sources = new HashSet<>();
            Set<TableId> targets = new HashSet<>();
            for (var table : job.tables()) {
                var sourceFactory = factories.source(table.source().get(JobOptions.TYPE));
                var sinkFactory = factories.sink(table.sink().get(JobOptions.TYPE));
                sourceFactory.validateDatasource(from.type());
                TableId source = tablePath(from, table.source());
                TableId target = tablePath(to, table.sink());
                if (!sources.add(source) || !targets.add(target)) {
                    throw new IllegalArgumentException("Duplicate resolved source or destination table");
                }
                if (schemas) {
                    sinkFactory.validateSchema(
                            table.sink(),
                            context.tableSchema(sourceId, source),
                            context.tableSchema(sinkId, target),
                            sourceFactory.producesChangelog());
                }
            }
            if (job.tables().size() > 1
                    && sourceId.equals(sinkId)
                    && sources.stream().anyMatch(targets::contains)) {
                throw new IllegalArgumentException("Batch destinations overlap their source tables");
            }
        } catch (RuntimeException failure) {
            throw new TaskPluginException("Invalid DATA_SYNC resource binding or Schema", failure);
        }
    }

    /** Resolves explicitly selected namespace values without changing the persisted configuration. */
    public static TableId tablePath(DataSourceInfo info, ReadonlyConfig options) {
        return info.resolveTable(
                options.get(JobOptions.DATABASE), options.get(JobOptions.SCHEMA), options.get(JobOptions.TABLE));
    }
}
