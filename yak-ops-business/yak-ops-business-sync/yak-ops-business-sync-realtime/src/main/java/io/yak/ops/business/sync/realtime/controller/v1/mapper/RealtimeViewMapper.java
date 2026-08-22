package io.yak.ops.business.sync.realtime.controller.v1.mapper;

import io.yak.ops.business.sync.realtime.controller.v1.vo.ComputeEnvironmentViews;
import io.yak.ops.business.sync.realtime.controller.v1.vo.RealtimeViews;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentDiagnosis;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView;
import io.yak.ops.business.sync.realtime.domain.RealtimeValidationResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RealtimeViewMapper {
  public RealtimeViews.Job toView(RealtimeJobView value) {
    if (value == null) return null;
    return new RealtimeViews.Job(value.id(), value.name(), value.description(), toView(value.spec()), value.runtimeEnvironmentId(), value.releaseState(), value.desiredState(), value.observedState(), value.definitionVersion(), value.publishedVersion(), value.configDigest(), value.lastError(), value.createTime(), value.updateTime(), toView(value.latestDeployment()));
  }
  public RealtimeViews.Page toView(RealtimeJobPage value) { return new RealtimeViews.Page(value.records().stream().map(this::toView).toList(), value.total(), value.pageNo(), value.pageSize()); }
  public RealtimeViews.Event toView(RealtimeJobEventView value) { return new RealtimeViews.Event(value.id(), value.deploymentId(), value.eventType(), value.fromState(), value.toState(), value.message(), value.createTime()); }
  public RealtimeViews.Validation toView(RealtimeValidationResult value) { return new RealtimeViews.Validation(value.valid(), value.deliverySemantics()); }
  public ComputeEnvironmentViews.Environment toView(ComputeEnvironment value) { return new ComputeEnvironmentViews.Environment(value.id(), value.name(), value.engineType(), value.deploymentMode(), value.submitterType(), toView(value.config()), value.enabled(), value.defaultEnvironment(), value.version(), value.createTime(), value.updateTime(), value.lastCheckStatus(), value.lastCheckMessage(), value.lastCheckTime()); }
  public ComputeEnvironmentViews.Diagnosis toView(ComputeEnvironmentDiagnosis value) { return new ComputeEnvironmentViews.Diagnosis(value.environmentId(), value.environmentName(), value.status(), value.ready(), value.summary(), value.detectedFlinkVersion(), value.detectedFlinkCdcVersion(), value.detectedJavaVersion(), value.checkedAt(), value.checks() == null ? List.of() : value.checks().stream().map(c -> new ComputeEnvironmentViews.Check(c.key(), c.label(), c.status(), c.message())).toList()); }
  public RealtimeViews.Observability toView(RealtimeObservabilityView value) { return new RealtimeViews.Observability(value.engineJobId(), value.flinkJobName(), value.flinkState(), value.startTime(), value.durationMs(), value.flinkWebUrl(), value.sampledAt(), toView(value.checkpoints()), toView(value.metrics())); }
  public RealtimeViews.RuntimeLog toView(RealtimeObservabilityView.RuntimeLog value) { return new RealtimeViews.RuntimeLog(value.rootException(), value.timestamp(), value.truncated(), value.exceptions().stream().map(e -> new RealtimeViews.RuntimeExceptionEntry(e.exceptionName(), e.stacktrace(), e.timestamp(), e.taskName(), e.taskManagerId(), e.endpoint())).toList()); }
  public RealtimeViews.Deployment toView(RealtimeJobView.Deployment value) { if (value == null) return null; return new RealtimeViews.Deployment(value.id(), value.definitionVersion(), value.specSummary(), value.configDigest(), value.idempotencyKey(), value.engineJobId(), value.runtimeRevision(), toView(value.runtimeEnvironment()), value.status(), value.resultUncertain(), value.errorMessage(), value.createTime(), value.updateTime()); }
  public RealtimeViews.PipelineSpec toView(CdcPipelineSpec value) { if (value == null) return null; return new RealtimeViews.PipelineSpec(value.sourceDataSourceRef(), value.sinkDataSourceRef(), value.tables().stream().map(t -> new RealtimeViews.TableRoute(t.sourceTable(), t.sinkTable(), t.matchMode() == null ? null : t.matchMode().name(), t.keyColumns())).toList(), value.startupMode(), value.schemaEvolution() == null ? null : value.schemaEvolution().name(), value.parallelism(), value.checkpointIntervalMs(), value.restart() == null ? null : new RealtimeViews.RestartPolicy(value.restart().strategy(), value.restart().attempts(), value.restart().delayMs()), value.sink() == null ? null : new RealtimeViews.SinkTuning(value.sink().maxRetries(), value.sink().batchSize(), value.sink().flushIntervalMs(), value.sink().maxBatchBytes(), value.sink().statementCacheSize(), value.sink().strictReplaySafety())); }
  private RealtimeViews.EnvironmentSnapshot toView(ComputeEnvironmentSnapshot value) { if (value == null) return null; return new RealtimeViews.EnvironmentSnapshot(value.id(), value.name(), value.engineType(), value.deploymentMode(), value.submitterType(), toView(value.config()), value.version()); }
  private RealtimeViews.RuntimeConfig toView(ComputeEnvironment.RuntimeConfig value) { if (value == null) return null; return new RealtimeViews.RuntimeConfig(value.restUrl(), value.flinkHome(), value.flinkCdcHome(), value.javaHome(), value.flinkVersion(), value.flinkCdcVersion(), value.ssh() == null ? null : new RealtimeViews.SshConfig(value.ssh().executable(), value.ssh().host(), value.ssh().port(), value.ssh().user(), value.ssh().identityFile(), value.ssh().knownHostsFile(), value.ssh().strictHostKeyChecking(), value.ssh().connectTimeoutSeconds(), value.ssh().remoteRestAddress(), value.ssh().remoteRestPort())); }
  private RealtimeViews.CheckpointSummary toView(RealtimeObservabilityView.CheckpointSummary value) { if (value == null) return null; return new RealtimeViews.CheckpointSummary(value.total(), value.completed(), value.failed(), value.inProgress(), value.restored(), toView(value.latestCompleted()), toView(value.latestFailed())); }
  private RealtimeViews.CheckpointDetail toView(RealtimeObservabilityView.CheckpointDetail value) { if (value == null) return null; return new RealtimeViews.CheckpointDetail(value.id(), value.triggerTimestamp(), value.latestAckTimestamp(), value.durationMs(), value.stateSizeBytes(), value.checkpointedSizeBytes(), value.acknowledgedSubtasks(), value.totalSubtasks(), value.failureMessage()); }
  private RealtimeViews.MetricSummary toView(RealtimeObservabilityView.MetricSummary value) { if (value == null) return null; return new RealtimeViews.MetricSummary(value.recordsRead(), value.recordsReadPerSecond(), value.recordsWritten(), value.recordsWrittenPerSecond(), value.bytesRead(), value.bytesReadPerSecond(), value.bytesWritten(), value.bytesWrittenPerSecond(), value.maxBusyMsPerSecond(), value.maxBackpressuredMsPerSecond(), value.maxIdleMsPerSecond(), value.vertexCount()); }
}
