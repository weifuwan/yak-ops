import apply_connectors
from apply_model import ROOT, P, C, J, edit, write

# Keep the Sink builder as the owner of batch defaults.
path=J+'sink/JdbcSinkFactory.java'
edit(path, '''        return new JdbcSink(new DefaultJdbcConnectionProvider(connection), JdbcFactoryLoader.loadDialect(connection.url()),
                plans, io.yak.ops.connector.base.sink.writer.BatchFlushPolicy.defaults(), first.metrics());''', '''        return JdbcSink.builder().withConnectionProvider(new DefaultJdbcConnectionProvider(connection))
                .withDialect(JdbcFactoryLoader.loadDialect(connection.url())).withTablePlans(plans)
                .withTableMetrics(first.metrics()).build();''')
path=J+'sink/JdbcSinkBuilder.java'
edit(path,'    private JdbcConnectionOptions connectionOptions;', '    private io.yak.ops.connector.base.metrics.TableMetrics metrics = new io.yak.ops.connector.base.metrics.TableMetrics();\n    private JdbcConnectionOptions connectionOptions;')
edit(path,'    public JdbcSinkBuilder withBatchFlushPolicy(BatchFlushPolicy policy) {', '''    /** Attaches the attempt-local measurement collector without affecting write or checkpoint behavior. */
    public JdbcSinkBuilder withTableMetrics(io.yak.ops.connector.base.metrics.TableMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        return this;
    }

    public JdbcSinkBuilder withBatchFlushPolicy(BatchFlushPolicy policy) {''')
edit(path,'new JdbcSink(provider, resolvedDialect, tablePlans, batchPolicy)', 'new JdbcSink(provider, resolvedDialect, tablePlans, batchPolicy, metrics)')
edit(path,'new JdbcSink(provider, resolvedDialect, plan, batchPolicy)', 'new JdbcSink(provider, resolvedDialect, List.of(plan), batchPolicy, metrics)')
path=P+'pipeline/PipelineBuilder.java'
edit(path,'import java.util.Map;', 'import java.util.Map;\nimport java.util.List;\nimport java.util.ArrayList;\nimport io.yak.ops.connector.base.metrics.TableMetrics;\nimport io.yak.ops.plugin.task.datasync.execution.TableExecutionProgress;')
edit(path,'/** Compiles an authorized JobPlan to a physical single-table YakFlow pipeline. */', '/** Compiles all admitted table routes into one physical YakFlow pipeline. */')
edit(path, '''        var sourceFactory = factories.source(plan.sourceParameters().get(JobOptions.TYPE));
        var sinkFactory = factories.sink(plan.sinkParameters().get(JobOptions.TYPE));''', '''        var first = plan.tables().getFirst();
        var sourceFactory = factories.source(first.sourceParameters().get(JobOptions.TYPE));
        var sinkFactory = factories.sink(first.sinkParameters().get(JobOptions.TYPE));''')
edit(path, '''        String sourceId = plan.sourceParameters().get(JobOptions.DATASOURCE_ID);''', '''        String sourceId = first.sourceParameters().get(JobOptions.DATASOURCE_ID);''')
edit(path, '''        String sinkId = plan.sinkParameters().get(JobOptions.DATASOURCE_ID);''', '''        String sinkId = first.sinkParameters().get(JobOptions.DATASOURCE_ID);''')
edit(path,'sourceId, plan.source().revision()', 'sourceId, first.source().revision()')
edit(path,'sinkId, plan.target().revision()', 'sinkId, first.target().revision()')
p=ROOT/path
text=p.read_text(); a=text.index('        ReadonlyConfig sourceOptions = creationOptions('); b=text.index('        SourceTransformation<TableRecord> input =',a)
text=text[:a]+'''        TableMetrics metrics = new TableMetrics();
        List<TableSourceFactoryContext> sourceContexts = new ArrayList<>();
        List<TableSinkFactoryContext> sinkContexts = new ArrayList<>();
        for (var table : plan.tables()) {
            metrics.register(table.source().table());
            ReadonlyConfig sourceOptions = creationOptions(sourceConnectorOptions, sourceFactory.factoryIdentifier(),
                    table.sourceOptions(), sourceConnection);
            ReadonlyConfig sinkOptions = creationOptions(sinkConnectorOptions, sinkFactory.factoryIdentifier(),
                    JobOptions.connectorOptions(table.sinkParameters()), targetConnection);
            sourceContexts.add(new TableSourceFactoryContext(sourceOptions, table.source().table(), table.source().schema(), metrics));
            sinkContexts.add(new TableSinkFactoryContext(sinkOptions, table.source().table(), table.source().schema(),
                    table.target().table(), table.target().schema(), metrics));
        }
        Source<TableRecord, ?, ?> source = sourceFactory.createSource(sourceContexts);
        if (source.getBoundedness() != sourceFactory.boundedness()) {
            throw new IllegalStateException("DATA_SYNC Source boundedness differs from its Factory contract");
        }
        Sink<TableRecord> sink = sinkFactory.createSink(sinkContexts);
''' + text[b:]
p.write_text(text)
edit(path,'        return new CompiledPipeline(graph, configuration);','''        TableExecutionProgress progress = null;
        if (source.getBoundedness() == io.yak.ops.core.api.connector.source.Boundedness.BOUNDED) {
            progress = new TableExecutionProgress(plan.tables(), metrics);
        }
        return new CompiledPipeline(graph, configuration, progress);''')
write(P+'pipeline/CompiledPipeline.java','''
package io.yak.ops.plugin.task.datasync.pipeline;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.plugin.task.datasync.execution.TableExecutionProgress;
import java.util.Objects;

/** One physical graph, its configuration, and optional bounded-table observations. */
public record CompiledPipeline(StreamGraph graph, Configuration configuration, TableExecutionProgress progress) {
    public CompiledPipeline {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(configuration, "configuration");
    }
    public CompiledPipeline(StreamGraph graph, Configuration configuration) { this(graph, configuration, null); }
}
''')
write(P+'execution/TableExecutionProgress.java','''
package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.data.TableId;
import io.yak.ops.plugin.task.datasync.plan.TablePlan;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/** Projects measured bounded-table work into a non-secret, versioned execution report. */
public final class TableExecutionProgress {
    private final List<TablePlan> plans;
    private final TableMetrics metrics;

    public TableExecutionProgress(List<TablePlan> plans, TableMetrics metrics) {
        this.plans = List.copyOf(plans);
        this.metrics = java.util.Objects.requireNonNull(metrics, "metrics");
    }

    /** Calculates a split-weighted percent only once every table's denominator is known. */
    public OptionalInt percent() {
        Map<TableId, TableMetrics.Snapshot> snapshot = metrics.snapshot();
        long total = 0;
        long completed = 0;
        for (var plan : plans) {
            var value = snapshot.get(plan.source().table());
            if (value == null || value.plannedSplits() == null) return OptionalInt.empty();
            total += value.plannedSplits();
            completed += Math.min(value.completedSplits(), value.plannedSplits());
        }
        if (total == 0) return OptionalInt.empty();
        return OptionalInt.of((int) Math.min(99, Math.floor(100.0 * completed / total)));
    }

    /** Serializes one immutable report; terminal failure does not erase already committed table work. */
    public String report(JobStatus jobStatus) {
        Map<TableId, TableMetrics.Snapshot> snapshot = metrics.snapshot();
        List<TableResult> tables = new ArrayList<>();
        for (int index = 0; index < plans.size(); index++) {
            var plan = plans.get(index);
            var value = snapshot.get(plan.source().table());
            if (value == null) value = new TableMetrics.Snapshot(null, 0, 0, 0, null);
            String state = "WAITING";
            boolean sourceComplete = value.plannedSplits() != null && value.completedSplits() >= value.plannedSplits();
            boolean committed = sourceComplete && value.committedRows() == value.readRows();
            if (value.failureCode() != null) state = "FAILED";
            else if (committed || jobStatus == JobStatus.FINISHED) state = "FINISHED";
            else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.CANCELED) state = "CANCELED";
            else if (value.plannedSplits() != null || value.readRows() > 0) state = "RUNNING";
            Integer percent = null;
            if ("FINISHED".equals(state)) percent = 100;
            else if (value.plannedSplits() != null && value.plannedSplits() > 0) {
                percent = (int) Math.min(99, Math.floor(100.0 * value.completedSplits() / value.plannedSplits()));
            }
            tables.add(new TableResult(index, "FULL", state, percent, "SPLITS", value.readRows(),
                    value.committedRows(), value.plannedSplits(), value.completedSplits(), value.failureCode()));
        }
        return JSONUtils.toJson(new Report(1, Instant.now().toString(), tables));
    }

    /** Report fields contain observations only; the matching table identities come from the pinned definition. */
    public record Report(int version, String observedAt, List<TableResult> tables) {
        public Report { tables = List.copyOf(tables); }
    }

    /** One mapping-indexed FULL-stage observation. Rows mean emitted and acknowledged input records, not affected rows. */
    public record TableResult(int mappingIndex, String step, String status, Integer progressPercent, String progressUnit,
            long readRows, long committedRows, Integer plannedSplits, long completedSplits, String failureCode) {}
}
''')
path=P+'execution/YakFlowTaskHandle.java'
edit(path,'    private final JobClient client;', '    private final JobClient client;\n    private final TableExecutionProgress progress;')
edit(path,'''        this.client = client;''', '''        this(client, null);
    }

    YakFlowTaskHandle(JobClient client, TableExecutionProgress progress) {
        this.client = client;
        this.progress = progress;''')
edit(path,'''        return client.getProgressPercent();''', '''        if (progress != null) return progress.percent();
        return client.getProgressPercent();''')
edit(path,'''    @Override
    public CompletionStage<TaskOutcome> completion() {''', '''    @Override
    public Optional<String> executionReport() {
        if (progress == null) return Optional.empty();
        var status = client.getJobStatus().getNow(null);
        if (status == null) return Optional.empty();
        return Optional.of(progress.report(status));
    }

    @Override
    public CompletionStage<TaskOutcome> completion() {''')
edit(P+'execution/DataSyncTaskExecutor.java','new YakFlowTaskHandle(client)', 'new YakFlowTaskHandle(client, pipeline.progress())')
api='yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-api/src/main/java/io/yak/ops/plugin/task/api/execution/TaskHandle.java'
# Insert before the public cancellation contract without depending on its exact JavaDoc text.
text=(ROOT/api).read_text(); pos=text.index('\n    /**'); text=text[:pos]+'''
    /**
     * Returns a plugin-owned public JSON observation of the current physical attempt.
     * No credentials, row values, checkpoint bytes or raw exception messages may be included.
     * The platform persists the final report atomically with the Instance outcome.
     *
     * @return immutable JSON snapshot up to 262144 characters, or empty when unavailable
     */
    default Optional<String> executionReport() { return Optional.empty(); }
''' + text[pos:]; (ROOT/api).write_text(text)
path='yak-ops-dao/src/main/java/io/yak/ops/dao/entity/task/InstanceEntity.java'
edit(path,'    private Integer progressPercent;', '''    private Integer progressPercent;
    /** Plugin-owned public JSON report, fixed with the terminal outcome; null when not observed. */
    private String executionReport;''')
write('yak-ops-dao/src/main/resources/db/migration/yak-ops/V13__task_instance_execution_report.sql','''
ALTER TABLE yak_ops_task_instance
  ADD COLUMN execution_report MEDIUMTEXT NULL COMMENT 'Plugin public execution report fixed with terminal outcome; null when unobserved';
''')
path='yak-ops-common/src/main/java/io/yak/ops/common/bean/vo/task/InstanceVO.java'
edit(path,'Integer progressPercent) {}','Integer progressPercent,\n        String executionReport) {}')
repo='yak-ops-dao/src/main/java/io/yak/ops/dao/repository/task/'
path=repo+'InstanceRepository.java'; text=(ROOT/path).read_text(); pos=text.rfind('}'); text=text[:pos]+'''
    /**
     * Persists a bounded plugin report under the active dispatch owner, in the caller's terminal transaction.
     *
     * @param workspaceId owning Workspace
     * @param instanceId active execution identity
     * @param dispatchToken winning physical dispatch token
     * @param report public JSON report without secrets or checkpoint state
     * @return true when the owner and active status still match
     */
    boolean updateExecutionReport(String workspaceId, String instanceId, String dispatchToken, String report);
''' + text[pos:]; (ROOT/path).write_text(text)
path=repo+'impl/InstanceRepositoryImpl.java'; text=(ROOT/path).read_text(); pos=text.rfind('}'); text=text[:pos]+'''
    @Override
    public boolean updateExecutionReport(String workspaceId, String instanceId, String dispatchToken, String report) {
        return mapper.update(null, Wrappers.<InstanceEntity>lambdaUpdate()
                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                .eq(InstanceEntity::getId, instanceId)
                .eq(InstanceEntity::getDispatchToken, dispatchToken)
                .in(InstanceEntity::getStatus, TaskInstanceStatus.LAUNCHING, TaskInstanceStatus.RUNNING, TaskInstanceStatus.CANCELLING)
                .set(InstanceEntity::getExecutionReport, report)) == 1;
    }
''' + text[pos:]; (ROOT/path).write_text(text)
biz='yak-ops-business/yak-ops-business-task/src/main/java/io/yak/ops/business/task/instance/'
path=biz+'InstanceLifecycleService.java'; text=(ROOT/path).read_text(); pos=text.rfind('}'); text=text[:pos]+'''
    /**
     * Atomically stores the final public plugin report, terminal outcome and release of owned claims.
     *
     * @param ticket winning physical execution ownership
     * @param outcome authoritative physical terminal outcome
     * @param executionReport plugin-owned JSON observation, or null when unavailable
     */
    void complete(DispatchTicket ticket, TaskOutcome outcome, String executionReport);
''' + text[pos:]; (ROOT/path).write_text(text)
path=biz+'impl/InstanceLifecycleServiceImpl.java'
edit(path,'''    public void complete(DispatchTicket ticket, TaskOutcome outcome) {
        InstanceEntity instance''', '''    public void complete(DispatchTicket ticket, TaskOutcome outcome) {
        complete(ticket, outcome, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(DispatchTicket ticket, TaskOutcome outcome, String executionReport) {
        InstanceEntity instance''')
edit(path,'''        TaskInstanceStatus status =
                switch (outcome) {''', '''        if (executionReport != null) {
            if (executionReport.length() > 262144 || !io.yak.ops.common.util.JSONUtils.readTree(executionReport).isObject()) {
                throw new IllegalArgumentException("Invalid public Task execution report");
            }
            if (!instances.updateExecutionReport(ticket.workspaceId(), ticket.instanceId(), ticket.dispatchToken(), executionReport)) {
                throw new IllegalStateException("Task execution report ownership changed");
            }
        }
        TaskInstanceStatus status =
                switch (outcome) {''')
path='yak-ops-task-execution/src/main/java/io/yak/ops/task/execution/dispatch/embedded/TaskExecutionObserver.java'
edit(path,'''    /** Requests cancellation for all active physical executions during process shutdown. */''', '''    /** Returns a process-owned observation only; terminal reads use the persisted Instance report. */
    public java.util.Optional<String> executionReport(String instanceId) {
        TaskHandle handle = active.get(instanceId);
        if (handle == null) return java.util.Optional.empty();
        return handle.executionReport();
    }

    /** Requests cancellation for all active physical executions during process shutdown. */''')
edit(path,'''                lifecycle.complete(ticket, outcome);''', '''                var report = handle.executionReport();
                if (report.isPresent()) lifecycle.complete(ticket, outcome, report.get());
                else lifecycle.complete(ticket, outcome);''')
path='yak-ops-boot/src/main/java/io/yak/ops/boot/controller/TaskInstanceController.java'
edit(path,'toInstance(instances.instance(WorkspaceContext.requireWorkspaceId(), id))','toInstance(instances.instance(WorkspaceContext.requireWorkspaceId(), id), true)')
edit(path,'''    private InstanceVO toInstance(InstanceEntity value) {''', '''    private InstanceVO toInstance(InstanceEntity value) {
        return toInstance(value, false);
    }

    private InstanceVO toInstance(InstanceEntity value, boolean includeReport) {''')
edit(path,'''        return new InstanceVO(''', '''        String report = null;
        if (includeReport) {
            report = value.getExecutionReport();
            if (!value.getStatus().isTerminal() && value.getStatus() != io.yak.ops.common.enums.task.TaskInstanceStatus.RECONCILING) {
                report = observer.executionReport(value.getId()).orElse(report);
            }
        }
        return new InstanceVO(''')
edit(path,'''                progress);''','''                progress,
                report);''')
print('One-job pipeline and atomic terminal report persistence applied')
