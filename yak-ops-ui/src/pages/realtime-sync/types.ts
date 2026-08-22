export type ReleaseState = 'DRAFT' | 'PUBLISHED';
export type DesiredState = 'RUNNING' | 'STOPPED';
export type ObservedState = 'STOPPED' | 'STARTING' | 'RUNNING' | 'STOPPING' | 'FAILED' | 'UNKNOWN' | 'CONFLICT';

export interface TableRoute {
  sourceTable: string;
  sinkTable: string;
  matchMode: 'EXACT' | 'REGEX';
  keyColumns: string[];
}

export interface RuntimeEnvironmentConfig {
  restUrl: string;
  flinkHome: string;
  flinkCdcHome: string;
  javaHome?: string;
  flinkVersion: string;
  flinkCdcVersion: string;
}

export interface ComputeEnvironmentOption {
  id: number;
  name: string;
  engineType: 'FLINK_CDC';
  deploymentMode: 'REMOTE';
  submitterType: 'LOCAL' | 'SSH';
  config: RuntimeEnvironmentConfig;
  enabled: boolean;
  defaultEnvironment: boolean;
  version: number;
  createTime?: string;
  updateTime?: string;
}

export interface ComputeEnvironmentSnapshot {
  id: number;
  name: string;
  engineType: string;
  deploymentMode: string;
  submitterType: string;
  config: RuntimeEnvironmentConfig;
  version: number;
}

export interface CdcPipelineSpec {
  sourceDataSourceRef: number;
  sinkDataSourceRef: number;
  tables: TableRoute[];
  startupMode: 'initial' | 'latest-offset';
  schemaEvolution: 'EVOLVE' | 'IGNORE' | 'FAIL';
  parallelism: number;
  checkpointIntervalMs: number;
  restart: {
    strategy: 'fixed-delay' | 'failure-rate' | 'none';
    attempts: number;
    delayMs: number;
  };
  sink: {
    maxRetries: number;
    batchSize: number;
    flushIntervalMs: number;
    maxBatchBytes: number;
    statementCacheSize: number;
    strictReplaySafety: boolean;
  };
}

export interface RealtimeDeployment {
  id: number;
  definitionVersion: number;
  specSummary?: string;
  configDigest: string;
  idempotencyKey: string;
  engineJobId?: string;
  runtimeRevision?: string;
  runtimeEnvironment: ComputeEnvironmentSnapshot;
  status: string;
  resultUncertain: boolean;
  errorMessage?: string;
  createTime: string;
  updateTime: string;
}

export interface RealtimeJob {
  id: number;
  name: string;
  description?: string;
  spec?: CdcPipelineSpec;
  runtimeEnvironmentId: number;
  releaseState: ReleaseState;
  desiredState: DesiredState;
  observedState: ObservedState;
  definitionVersion: number;
  publishedVersion?: number;
  configDigest?: string;
  lastError?: string;
  createTime: string;
  updateTime: string;
  latestDeployment?: RealtimeDeployment;
}

export interface RealtimeJobPage {
  records: RealtimeJob[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface RealtimeEvent {
  id: number;
  deploymentId?: number;
  eventType: string;
  fromState?: string;
  toState?: string;
  message?: string;
  createTime: string;
}

export interface RealtimeJobChange {
  definitionId: number;
  eventType: string;
  fromState?: string;
  toState?: string;
  message?: string;
}

export interface RealtimeCheckpointDetail {
  id?: number;
  triggerTimestamp?: number;
  latestAckTimestamp?: number;
  durationMs?: number;
  stateSizeBytes?: number;
  checkpointedSizeBytes?: number;
  acknowledgedSubtasks?: number;
  totalSubtasks?: number;
  failureMessage?: string;
}

export interface RealtimeCheckpointSummary {
  total: number;
  completed: number;
  failed: number;
  inProgress: number;
  restored: number;
  latestCompleted?: RealtimeCheckpointDetail;
  latestFailed?: RealtimeCheckpointDetail;
}

export interface RealtimeMetricSummary {
  recordsRead?: number;
  recordsReadPerSecond?: number;
  recordsWritten?: number;
  recordsWrittenPerSecond?: number;
  bytesRead?: number;
  bytesReadPerSecond?: number;
  bytesWritten?: number;
  bytesWrittenPerSecond?: number;
  maxBusyMsPerSecond?: number;
  maxBackpressuredMsPerSecond?: number;
  maxIdleMsPerSecond?: number;
  vertexCount: number;
}

export interface RealtimeObservability {
  engineJobId: string;
  flinkJobName?: string;
  flinkState?: string;
  startTime?: number;
  durationMs?: number;
  flinkWebUrl?: string;
  sampledAt: number;
  checkpoints: RealtimeCheckpointSummary;
  metrics: RealtimeMetricSummary;
}

export interface RealtimeRuntimeException {
  exceptionName?: string;
  stacktrace?: string;
  timestamp?: number;
  taskName?: string;
  taskManagerId?: string;
  endpoint?: string;
}

export interface RealtimeRuntimeLog {
  rootException?: string;
  timestamp?: number;
  truncated: boolean;
  exceptions: RealtimeRuntimeException[];
}

export interface DataSourceOption {
  label: string;
  value: string;
  dbType: string;
}

export interface DataSourceCatalogTable {
  database?: string;
  schema?: string;
  name: string;
  type?: string;
  remarks?: string;
}

export interface DataSourceCatalogColumn {
  name: string;
  typeName?: string;
  jdbcType?: number;
  size?: number;
  scale?: number;
  nullable?: boolean;
  ordinalPosition?: number;
  primaryKey?: boolean;
  remarks?: string;
}

export interface RuntimeCapabilities {
  engineType?: string;
  runtimeVersion?: string;
  runtimeEnvironmentId?: number;
  runtimeEnvironmentName?: string;
  runtimeEnvironmentVersion?: number;
  restUrl?: string;
  restTransport?: 'DIRECT';
  submissionMode?: 'LOCAL' | 'SSH';
  submissionEndpoint?: string;
  flinkVersion?: string;
  flinkCdcVersion?: string;
  deliverySemantics?: string;
  connectors?: {
    sources?: string[];
    sinks?: string[];
    schemaEvolution?: string[];
  };
  checkpointsApi?: boolean;
  metricsApi?: boolean;
  checkpointConfiguration?: boolean;
  restartConfiguration?: boolean;
  protocolCompatible?: boolean;
  deployEnabled?: boolean;
  deployDisabledReason?: string;
}

export interface ApiResponse<T> {
  code: number;
  data: T;
  msg?: string;
  message?: string;
}
