from pathlib import Path
from textwrap import dedent

ROOT = Path.cwd()
P = 'yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-data-sync/src/main/java/io/yak/ops/plugin/task/datasync/'
C = 'yak-flow/yak-flow-connector-base/src/main/java/io/yak/ops/connector/base/'
J = 'yak-flow/yak-flow-connector-jdbc/src/main/java/io/yak/ops/connector/jdbc/'

def edit(path, old, new, count=1):
    p = ROOT / path
    text = p.read_text()
    assert text.count(old) == count, (path, text.count(old), old[:100])
    p.write_text(text.replace(old, new))

def write(path, text):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(dedent(text).lstrip('\n').rstrip() + '\n')

edit(P+'solution/SolutionType.java', 'SINGLE_TABLE_OFFLINE("单表离线", "JDBC", List.of(Step.FULL), true),', 'SINGLE_TABLE_OFFLINE("单表离线", "JDBC", List.of(Step.FULL), true),\n    DATABASE_OFFLINE("整库离线", "JDBC", List.of(Step.FULL), true),')
edit(P+'solution/SolutionCatalog.java', '        return List.copyOf(choices);', '        choices.add(solution(SolutionType.DATABASE_OFFLINE));\n        return List.copyOf(choices);')
edit(P+'solution/SolutionCatalog.java', 'if (type == SolutionType.SINGLE_TABLE_OFFLINE)', 'if (type.supportsScheduling())')
write(P+'config/TableConfig.java', '''
package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.ReadonlyConfig;
import java.util.Objects;

/** One explicit table mapping projected into validated, non-secret Connector options. */
public record TableConfig(ReadonlyConfig source, ReadonlyConfig sink) {
    public TableConfig {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sink, "sink");
    }
}
''')
# Preserve v2 parsing and the single-table accessors, but make a multi-table projection explicit.
path=P+'config/JobConfig.java'
edit(path, '    private final SolutionType solutionType;', '    private final SolutionType solutionType;\n    private final List<TableConfig> tables;\n\n    /** Maximum explicit mappings admitted by one product task. */\n    public static final int MAX_TABLES = 100;')
edit(path, '        this.solutionType = solutionType;\n    }', '        this.solutionType = solutionType;\n        this.tables = List.of(new TableConfig(source, sink));\n    }\n\n    private JobConfig(ReadonlyConfig parameters, List<TableConfig> tables, SolutionType type) {\n        this.parameters = parameters;\n        this.tables = List.copyOf(tables);\n        this.source = null;\n        this.sink = null;\n        this.solutionType = type;\n    }')
edit(path, '''        if (mappings.size() != 1 || !(mappings.getFirst() instanceof Map<?, ?> mapping)) {
            throw new IllegalArgumentException("Single-table synchronization requires exactly one table mapping");
        }
        Map<String, Object> values = new LinkedHashMap<>();''', '''        if (mappings.isEmpty() || mappings.size() > MAX_TABLES
                || (type != SolutionType.DATABASE_OFFLINE && mappings.size() != 1)) {
            throw new IllegalArgumentException("Unsupported explicit table mapping count");
        }
        List<TableConfig> tables = new java.util.ArrayList<>();
        java.util.Set<List<String>> sources = new HashSet<>();
        java.util.Set<List<String>> targets = new HashSet<>();
        for (Object entryValue : mappings) {
            if (!(entryValue instanceof Map<?, ?> mapping)) {
                throw new IllegalArgumentException("Table mapping must be an object");
            }
        Map<String, Object> values = new LinkedHashMap<>();''')
edit(path, '''        Map<String, Object> canonical = new LinkedHashMap<>(parameters.getSourceMap());''', '''        if (!sources.add(tableKey(sourceTable)) || !targets.add(tableKey(targetTable))) {
            throw new IllegalArgumentException("Source and target tables must be unique");
        }
        tables.add(new TableConfig(ReadonlyConfig.fromMap(input), ReadonlyConfig.fromMap(output)));
        }
        Map<String, Object> canonical = new LinkedHashMap<>(parameters.getSourceMap());''')
edit(path, '''        return new JobConfig(
                ReadonlyConfig.fromMap(canonical), ReadonlyConfig.fromMap(input), ReadonlyConfig.fromMap(output), type);''', '''        return new JobConfig(ReadonlyConfig.fromMap(canonical), tables, type);''')
edit(path, '''    private static JobConfig pipeline(ReadonlyConfig parameters) {''', '''    private static List<String> tableKey(ReadonlyConfig table) {
        return java.util.Arrays.asList(
                table.get(JobOptions.DATABASE), table.get(JobOptions.SCHEMA), table.get(JobOptions.TABLE));
    }

    /** Returns the complete immutable explicit table mapping collection. */
    public List<TableConfig> tables() {
        return tables;
    }

    /** Returns the shared source connection identity, independent of table cardinality. */
    public String sourceDataSourceId() {
        return tables.getFirst().source().get(JobOptions.DATASOURCE_ID);
    }

    /** Returns the shared destination connection identity. */
    public String targetDataSourceId() {
        return tables.getFirst().sink().get(JobOptions.DATASOURCE_ID);
    }

    private static JobConfig pipeline(ReadonlyConfig parameters) {''')
edit(path, '''        return source;''', '''        if (tables.size() != 1) throw new IllegalStateException("Use explicit table mappings for a multi-table task");
        return tables.getFirst().source();''')
edit(path, '''        return sink;''', '''        if (tables.size() != 1) throw new IllegalStateException("Use explicit table mappings for a multi-table task");
        return tables.getFirst().sink();''')
# Remove redundant fields once their callers use the collection.
edit(path, '    private final ReadonlyConfig source;\n    private final ReadonlyConfig sink;\n', '')
edit(path, '        this.source = source;\n        this.sink = sink;\n', '')
edit(path, '        this.source = null;\n        this.sink = null;\n', '')
path=P+'DataSyncTaskPlugin.java'
for old,new in [('before.source().get(JobOptions.DATASOURCE_ID)','before.sourceDataSourceId()'),('after.source().get(JobOptions.DATASOURCE_ID)','after.sourceDataSourceId()'),('before.sink().get(JobOptions.DATASOURCE_ID)','before.targetDataSourceId()'),('after.sink().get(JobOptions.DATASOURCE_ID)','after.targetDataSourceId()')]: edit(path,old,new)
edit(path,'JobConfig.from(parameters).source().get(JobOptions.TYPE)','JobConfig.from(parameters).solutionType().sourceIdentifier()')
write(P+'validation/JobConfigValidator.java', '''
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
    public static void validateReferences(ReadonlyConfig parameters, TaskValidationContext context, FactoryUtil factories) {
        validateBindings(parameters, context, factories, false);
    }

    /** Validates all authorized source/target schemas; no DDL or writes are performed. */
    public static void validateResources(ReadonlyConfig parameters, TaskValidationContext context, FactoryUtil factories) {
        validateBindings(parameters, context, factories, true);
    }

    private static void validateBindings(ReadonlyConfig parameters, TaskValidationContext context,
            FactoryUtil factories, boolean schemas) {
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
                    sinkFactory.validateSchema(table.sink(), context.tableSchema(sourceId, source),
                            context.tableSchema(sinkId, target), sourceFactory.producesChangelog());
                }
            }
            if (job.tables().size() > 1 && sourceId.equals(sinkId)
                    && sources.stream().anyMatch(targets::contains)) {
                throw new IllegalArgumentException("Batch destinations overlap their source tables");
            }
        } catch (RuntimeException failure) {
            throw new TaskPluginException("Invalid DATA_SYNC resource binding or Schema", failure);
        }
    }

    /** Resolves explicitly selected namespace values without changing the persisted configuration. */
    public static TableId tablePath(DataSourceInfo info, ReadonlyConfig options) {
        return info.resolveTable(options.get(JobOptions.DATABASE), options.get(JobOptions.SCHEMA), options.get(JobOptions.TABLE));
    }
}
''')
write(P+'plan/TablePlan.java','''
package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.util.Objects;

/** Resolved source-to-target metadata and non-secret options for one explicit table mapping. */
public record TablePlan(ReadonlyConfig sourceParameters, ReadonlyConfig sinkParameters,
        DataSourceTable source, DataSourceTable target, ReadonlyConfig sourceOptions) {
    public TablePlan {
        Objects.requireNonNull(sourceParameters, "sourceParameters");
        Objects.requireNonNull(sinkParameters, "sinkParameters");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(sourceOptions, "sourceOptions");
    }
}
''')
write(P+'plan/JobPlan.java','''
package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import java.util.List;
import java.util.Objects;

/** Complete immutable table plan and atomic resource admission for one physical Job. */
public record JobPlan(List<TablePlan> tables, TaskAdmissionPlan admissionPlan) {
    public JobPlan {
        tables = List.copyOf(tables);
        if (tables.isEmpty()) throw new IllegalArgumentException("Job needs table mappings");
        Objects.requireNonNull(admissionPlan, "admissionPlan");
    }

    /** Constructs a single-table plan using the same collection-based execution contract. */
    public JobPlan(ReadonlyConfig sourceParameters, ReadonlyConfig sinkParameters,
            DataSourceTable source, DataSourceTable target, TaskAdmissionPlan admissionPlan, ReadonlyConfig sourceOptions) {
        this(List.of(new TablePlan(sourceParameters, sinkParameters, source, target, sourceOptions)), admissionPlan);
    }

    private TablePlan single() {
        if (tables.size() != 1) throw new IllegalStateException("Job has multiple table mappings");
        return tables.getFirst();
    }
    public ReadonlyConfig sourceParameters() { return single().sourceParameters(); }
    public ReadonlyConfig sinkParameters() { return single().sinkParameters(); }
    public DataSourceTable source() { return single().source(); }
    public DataSourceTable target() { return single().target(); }
    public ReadonlyConfig sourceOptions() { return single().sourceOptions(); }
}
''')
path=P+'plan/JobPlanner.java'
edit(path, '''        ReadonlyConfig source = job.source();
        ReadonlyConfig sink = job.sink();''', '''        List<TablePlan> tables = new ArrayList<>();
        List<ExecutionResource> claims = new ArrayList<>();
        java.util.Set<TableId> sources = new java.util.HashSet<>();
        java.util.Set<TableId> targets = new java.util.HashSet<>();
        StringBuilder bindings = new StringBuilder();
        for (var mapping : job.tables()) {
        ReadonlyConfig source = mapping.source();
        ReadonlyConfig sink = mapping.sink();''')
edit(path, '''        sourceFactory.validateDatasource(from.datasourceType());''', '''        sourceFactory.validateDatasource(from.datasourceType());
        io.yak.ops.plugin.task.datasync.solution.SolutionCatalog.validate(
                job.solutionType(), from.datasourceType(), to.datasourceType());
        if (!sources.add(from.table()) || !targets.add(to.table())) {
            throw new IllegalArgumentException("Duplicate resolved source or destination table");
        }
        if (!tables.isEmpty() && (from.revision() != tables.getFirst().source().revision()
                || to.revision() != tables.getFirst().target().revision())) {
            throw new IllegalStateException("Datasource revision changed while planning the table collection");
        }''')
edit(path, '''        List<ExecutionResource> claims = new ArrayList<>();
        claims.add(new ExecutionResource("DATA_SYNC_TARGET", target));''', '''        claims.add(new ExecutionResource("DATA_SYNC_TARGET", target));''')
edit(path, '''        TaskAdmissionPlan admission = new TaskAdmissionPlan(claims, sha256(binding));
        return new JobPlan(source, sink, from, to, admission, ReadonlyConfig.fromMap(prepared));''', '''        bindings.append(binding);
        tables.add(new TablePlan(source, sink, from, to, ReadonlyConfig.fromMap(prepared)));
        }
        if (tables.size() > 1 && job.sourceDataSourceId().equals(job.targetDataSourceId())
                && sources.stream().anyMatch(targets::contains)) {
            throw new IllegalArgumentException("Batch destinations overlap their source tables");
        }
        return new JobPlan(tables, new TaskAdmissionPlan(claims, sha256(bindings.toString())));''')
# Do not classify DATABASE_OFFLINE as unrecognized in mixed-version paging or Operations metrics.
path='yak-ops-dao/src/main/java/io/yak/ops/dao/mapper/task/DefinitionSqlProvider.java'
edit(path,"WHEN 'SINGLE_TABLE_OFFLINE' THEN 'JDBC'", "WHEN 'SINGLE_TABLE_OFFLINE' THEN 'JDBC'\n                  WHEN 'DATABASE_OFFLINE' THEN 'JDBC'")
print('Product model and all-table planning applied')
