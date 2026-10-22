package io.yak.ops.plugin.task.datasync.validation;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.data.TableId;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskValidationException;
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
            SolutionCatalog.validateSteps(from.type(), to.type(), job.steps());
            if (job.solutionType().supportsScheduling()
                    && job.steps().contains(io.yak.ops.plugin.task.datasync.solution.Step.INCREMENTAL)
                    && (!java.util.Set.of("MYSQL", "POSTGRE_SQL").contains(from.type())
                            || !java.util.Set.of("MYSQL", "POSTGRE_SQL").contains(to.type()))) {
                throw new IllegalArgumentException("Offline incremental supports MySQL and PostgreSQL only");
            }
            boolean prepareSchema = job.steps().contains(io.yak.ops.plugin.task.datasync.solution.Step.SCHEMA);
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
                if (prepareSchema) SchemaValidator.validateNamespace(to, target);
                if (schemas && prepareSchema) {
                    var sourceTable = context.tableMetadata(sourceId, source);
                    sourceFactory.validateSchema(sourceTable.schema());
                    var event = SchemaValidator.createEvent(sourceTable, to, target);
                    sinkFactory.validateSchema(
                            table.sink(), sourceTable.schema(), event.getSchema(), sourceFactory.producesChangelog());
                    context.findTable(sinkId, target).ifPresent(actual -> {
                        if (actual.revision() != to.revision())
                            throw new IllegalStateException("Target datasource revision changed during validation");
                        SchemaValidator.validateTarget(event, actual);
                        sinkFactory.validateSchema(
                                table.sink(), sourceTable.schema(), actual.schema(), sourceFactory.producesChangelog());
                    });
                } else if (schemas) {
                    var sourceSchema = context.tableSchema(sourceId, source);
                    sourceFactory.validateSchema(sourceSchema);
                    sinkFactory.validateSchema(
                            table.sink(),
                            sourceSchema,
                            context.tableSchema(sinkId, target),
                            sourceFactory.producesChangelog());
                }
            }
            if (sourceId.equals(sinkId) && sources.stream().anyMatch(targets::contains)) {
                throw new IllegalArgumentException("Batch destinations overlap their source tables");
            }
        } catch (RuntimeException failure) {
            if (schemas && failure instanceof SchemaValidationException schemaFailure) {
                throw new TaskValidationException(
                        "SCHEMA_VALIDATION", safeSchemaMessage(schemaFailure.failure()), schemaFailure);
            }
            throw new TaskPluginException("Invalid DATA_SYNC resource binding or Schema", failure);
        }
    }

    /**
     * Only fixed diagnostic prefixes and simple column identifiers may reach the client.
     * Native driver exceptions, raw DDL, credentials and configuration values are excluded.
     */
    private static String safeSchemaMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message != null) {
            String[][] reasons = {
                {"Unsupported default expression for column: ", "不支持的默认值表达式"},
                {"Unknown default expression kind for column: ", "无法识别默认值是否为表达式"},
                {"Unsupported default literal escaping for column: ", "默认值含有暂不支持的转义字符"},
                {"Target column properties unavailable: ", "目标字段物理属性缺失"},
                {"CURRENT_TIMESTAMP requires a timestamp column: ", "时间默认值与字段类型不匹配"},
                {"Unsupported ON UPDATE expression for column: ", "暂不支持的自动更新时间表达式"},
                {"AUTO_INCREMENT requires an integer primary key: ", "自增列必须是整型主键"},
                {"Generated column requires a verified expression and write projection: ", "生成列尚不支持安全写入"},
                {"Unknown generation metadata for column: ", "无法确认字段生成规则"},
                {"Unknown target generation metadata for column: ", "无法确认目标字段生成规则"},
                {"Target column properties do not match: ", "目标字段属性与来源不一致"},
                {"Unexpected target column properties: ", "目标表存在不兼容的字段属性"},
                {"Target dialect cannot preserve column properties: ", "目标数据库暂不支持该字段属性"}
            };
            for (String[] reason : reasons) {
                if (!message.startsWith(reason[0])) continue;
                String column = message.substring(reason[0].length());
                if (column.matches("[A-Za-z_][A-Za-z_0-9]{0,127}")) {
                    return "字段 " + column + "：" + reason[1];
                }
            }
        }
        return "目标表结构不兼容，请检查字段类型和默认值等属性";
    }

    /** Resolves explicitly selected namespace values without changing the persisted configuration. */
    public static TableId tablePath(DataSourceInfo info, ReadonlyConfig options) {
        return info.resolveTable(
                options.get(JobOptions.DATABASE), options.get(JobOptions.SCHEMA), options.get(JobOptions.TABLE));
    }
}
