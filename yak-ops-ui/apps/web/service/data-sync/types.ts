import type { TaskInstanceStatus } from "../task-execution/types";

export type DataSyncWriteMode = "APPEND" | "UPSERT" | "RETRACT";

export interface PaginationInfo {
  pageNo: number;
  pageSize: number;
  total: number;
  pages?: number;
}

export type DataSyncType = "OFFLINE" | "REALTIME";

export type DataSyncProductType = "SINGLE_TABLE_BATCH" | "DATABASE_REPLICATION";
export type DataSyncStep = "SCHEMA" | "FULL" | "INCREMENTAL";

export interface DataSyncSolution {
  id: string;
  sourceConnector: string;
  sinkConnector: string;
  producesChangelog: boolean;
  productType: DataSyncProductType;
  displayName: string;
  steps: DataSyncStep[];
  defaultSteps: DataSyncStep[];
  allowedCombinations: Array<{
    steps: DataSyncStep[];
    supportsScheduling: boolean;
    writeModes: DataSyncWriteMode[];
  }>;
}

export interface DataSyncDraft {
  sourceConnector: string;
  sinkConnector: string;
  sourceProducesChangelog: boolean;
  sourceType: string;
  targetType: string;
  productType: DataSyncProductType;
  steps: DataSyncStep[];
  name: string;
  form?: DataSyncTaskSavePayload;
}

export interface DataSyncTablePath {
  database?: string;
  schema?: string;
  table: string;
}

export interface DataSyncIncrementalConfig {
  mode: "TIMESTAMP" | "INCREASING";
  column: string;
  initialValue?: string;
  delaySeconds?: number;
  lookbackSeconds?: number;
}

export interface DataSyncTableMapping {
  source: DataSyncTablePath & { chunkKeyColumn?: string };
  target: DataSyncTablePath & { primaryKeys?: string[] };
  writeMode: DataSyncWriteMode;
  incremental?: DataSyncIncrementalConfig;
}

export interface DataSyncTableResult {
  mappingIndex: number;
  step: "FULL" | "INCREMENTAL";
  status: "WAITING" | "RUNNING" | "FINISHED" | "FAILED" | "CANCELED";
  progressPercent: number | null;
  progressUnit: "SPLITS";
  readRows: number;
  committedRows: number;
  plannedSplits: number | null;
  completedSplits: number;
  failureCode: string | null;
  plannedTables?: number | null;
  completedTables?: number | null;
  action?: "PREPARED" | "VERIFIED" | null;
}

export interface DataSyncBatchReport {
  version: 1;
  observedAt: string;
  tables: DataSyncTableResult[];
}

export type DataSyncStageStatus =
  | "WAITING"
  | "RUNNING"
  | "FINISHED"
  | "FAILED"
  | "CANCELED"
  | "UNKNOWN";

export interface DataSyncStageResult {
  step: DataSyncStep;
  status: DataSyncStageStatus;
  progressPercent: number | null;
  progressUnit: "SPLITS" | "RECORDS" | "TABLES";
  readRows: number | null;
  committedRows: number | null;
  plannedSplits: number | null;
  completedSplits: number | null;
  failureCode: string | null;
  plannedTables?: number | null;
  completedTables?: number | null;
  action?: "PREPARED" | "VERIFIED" | null;
}

export interface DataSyncExecutionSummary {
  version: 2 | 3;
  observedAt: string;
  generation: number;
  restored: boolean;
  counterScope: "GENERATION" | "INSTANCE";
  phase: "SCHEMA" | "FULL" | "INCREMENTAL" | "INITIALIZING" | "SNAPSHOT" | "HANDOFF" | "STREAMING";
  checkpoints: { handoffId: number | null; completedId: number | null };
  stages: DataSyncStageResult[];
}

export interface DataSyncStageReport extends DataSyncExecutionSummary {
  tables: Array<{ mappingIndex: number; stages: DataSyncStageResult[] }>;
}

export type DataSyncExecutionReport = DataSyncBatchReport | DataSyncStageReport;

export interface DataSyncTransform {
  "source-table": string;
  projection?: string;
  filter?: string;
  "primary-keys"?: string;
  "partition-keys"?: string;
  "table-options"?: string;
  "table-options.delimiter"?: string;
  description?: string;
  "converter-after-transform"?: string;
}

/** Supported upstream options retained even when the editor has no form field for them. */
export interface DataSyncPipelineOptions {
  name?: string;
  parallelism?: number | string;
  "local-time-zone"?: string;
  "schema.change.behavior"?: string;
  "schema.operator.uid"?: string;
  "route-mode"?: string;
  "transform.decimal.precision.mode"?: string;
}

/** Task JSON uses installed Connector identifiers; the Task Plugin converts it to Pipeline YAML. */
export interface DataSyncParameters {
  source: {
    type: string;
    datasourceId: string;
    syncMode?: "FULL" | "INCREMENTAL" | "FULL_AND_INCREMENTAL";
    startup?: {
      mode:
        | "INITIAL"
        | "SNAPSHOT"
        | "EARLIEST_OFFSET"
        | "LATEST_OFFSET"
        | "SPECIFIC_OFFSETS"
        | "TIMESTAMP";
      file?: string;
      pos?: number;
      gtidSet?: string;
      timestampMillis?: number;
      skipEvents?: number;
      skipRows?: number;
    };
    tables: Array<
      DataSyncTablePath & {
        snapshot?: { chunkKeyColumn: string };
        incremental?: DataSyncIncrementalConfig;
      }
    >;
  };
  sink: {
    type: string;
    datasourceId: string;
    schemaPolicy: {
      initial: "VERIFY" | "CREATE_OR_ALTER";
      evolution: { enabled: boolean; allowDropColumns: boolean };
    };
    tables: Array<
      DataSyncTablePath & {
        write: {
          mode: DataSyncWriteMode;
          primaryKeys?: string[];
          transaction?: { mode: "CHECKPOINT_XA" };
        };
      }
    >;
  };
  route: Array<{ "source-table": string; "sink-table": string }>;
  transform: DataSyncTransform[];
  pipeline: DataSyncPipelineOptions & {
    "execution.runtime-mode": "BATCH" | "STREAMING";
    /** Effective execution scope, independent of the application product profile. */
    "pipeline.table-scope"?: "SINGLE" | "DATABASE";
  };
}

/** MySQL CDC startup position, including specific file/GTID/timestamp fields. */
export type DataSyncStartupOptions = NonNullable<DataSyncParameters["source"]["startup"]>;

export type DataSyncTaskStatus = "UNPUBLISHED" | "PUBLISHED";

export interface DataSyncTaskRecord {
  tableScope?: "SINGLE" | "DATABASE";
  sourceConnector: string;
  sinkConnector: string;
  sourceProducesChangelog: boolean;
  transforms: DataSyncTransform[];
  pipelineOptions: DataSyncPipelineOptions;
  id: string;
  /** Persisted definition can be displayed, but fails current static execution validation. */
  configurationError?: string;
  chunkKeyColumn?: string;
  targetPrimaryKeys?: string[];
  schemaChangesEnabled?: boolean;
  allowDropColumns?: boolean;
  tableMappings: DataSyncTableMapping[];
  incremental?: DataSyncIncrementalConfig;
  /** Read-only in the current editor; preserve the full CDC startup intent on parameter edits. */
  startup?: DataSyncStartupOptions;
  productType: DataSyncProductType;
  steps: DataSyncStep[];
  name: string;
  syncType: DataSyncType | string;
  status: DataSyncTaskStatus | string;
  debug: boolean;
  writeMode: DataSyncWriteMode | string;
  sourceDataSourceId: string;
  sourceDatabase?: string;
  sourceSchema?: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase?: string;
  targetSchema?: string;
  targetTable: string;
  definitionVersion: number;
  revision: number;
  publishedVersion: number | null;
  publishedDebug: boolean | null;
  hasUnpublishedChanges: boolean;
  remark?: string;
  updateBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DataSyncTaskPageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  syncType?: DataSyncType;
  productType?: DataSyncProductType;
  status?: DataSyncTaskStatus;
  sourceDataSourceId?: string;
  targetDataSourceId?: string;
}

/** An unreadable saved version is still a task row, but cannot perform runtime actions. */
export interface DataSyncUnreadableTaskRecord {
  id: string;
  name: string;
  status: DataSyncTaskStatus | string;
  definitionVersion: number;
  revision: number;
  publishedVersion: number | null;
  publishedDebug: boolean | null;
  hasUnpublishedChanges: boolean;
  updateBy?: string;
  updateTime?: string;
  configurationError: string;
}

export type DataSyncTaskListRecord = DataSyncTaskRecord | DataSyncUnreadableTaskRecord;

export interface DataSyncTaskPageResult {
  bizData: DataSyncTaskListRecord[];
  pagination: PaginationInfo;
}

export interface TaskDefinitionRecord {
  id: string;
  name: string;
  taskType: string;
  status: DataSyncTaskStatus | string;
  definitionVersion: number;
  revision: number;
  publishedVersion: number | null;
  publishedDebug: boolean | null;
  hasUnpublishedChanges: boolean;
  parameterSchemaVersion: number;
  parameters: DataSyncParameters & { index?: Record<string, string> };
  debug: boolean;
  remark?: string;
  createTime?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface TaskDefinitionPageResult {
  bizData: TaskDefinitionRecord[];
  pagination: PaginationInfo;
}

export interface TaskParameterVersionSummary {
  definitionId: string;
  version: number;
  parameterSchemaVersion: number;
  createBy: string;
  createTime: string;
}

/** Historical JSON is inspected before schema-specific editing is allowed. */
export interface TaskParameterVersion extends TaskParameterVersionSummary {
  parameters: unknown;
}

export interface TaskPublicationRecord {
  id: string;
  definitionId: string;
  version: number;
  debug: boolean;
  requestId: string;
  createBy: string;
  createTime: string;
}

export interface TaskVersionPage<T> {
  bizData: T[];
  pagination: PaginationInfo;
}

export interface PublishTaskRequest {
  version: number;
  expectedRevision: number;
  requestId: string;
}

interface DataSyncTaskSaveBase {
  name: string;
  debug: boolean;
  sourceDataSourceId: string;
  sourceDatabase?: string;
  sourceSchema?: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase?: string;
  targetSchema?: string;
  targetTable: string;
  remark?: string;
}

export interface DataSyncTaskSavePayload extends DataSyncTaskSaveBase {
  tableScope?: "SINGLE" | "DATABASE";
  sourceConnector: string;
  sinkConnector: string;
  sourceProducesChangelog: boolean;
  transforms?: DataSyncTransform[];
  pipelineOptions?: DataSyncPipelineOptions;
  chunkKeyColumn?: string;
  targetPrimaryKeys?: string[];
  schemaChangesEnabled?: boolean;
  allowDropColumns?: boolean;
  tableMappings?: DataSyncTableMapping[];
  productType: DataSyncProductType;
  steps: DataSyncStep[];
  writeMode: DataSyncWriteMode;
  incremental?: DataSyncIncrementalConfig;
  startup?: DataSyncStartupOptions;
}

export type DataSyncTaskUpdatePayload =
  | DataSyncTaskSavePayload
  | Pick<DataSyncTaskSaveBase, "name" | "remark" | "debug">;

export type DataSyncInstanceStatus = TaskInstanceStatus;

export interface DataSyncEndpointSnapshot {
  dataSourceId: string;
  dataSourceName?: string;
  dataSourceType?: string;
  database?: string;
  schema?: string;
  table: string;
}

export interface DataSyncDefinitionSnapshot {
  tableMappings?: DataSyncTableMapping[];
  taskId: string;
  productType?: DataSyncProductType;
  steps?: DataSyncStep[];
  taskName: string;
  taskVersion: number;
  syncType?: DataSyncType | string;
  writeMode?: DataSyncWriteMode | string;
  source: DataSyncEndpointSnapshot;
  target: DataSyncEndpointSnapshot;
}

export interface DataSyncInstanceRecord {
  executionReport?: DataSyncExecutionReport;
  id: string;
  productType: DataSyncProductType;
  steps: DataSyncStep[];
  taskId: string;
  taskName: string;
  taskVersion: number;
  syncType: DataSyncType | string;
  triggerType?: "MANUAL" | "SCHEDULE" | "WORKFLOW";
  scheduleId?: string | null;
  scheduleRevision?: number | null;
  scheduledTime?: string | null;
  status: DataSyncInstanceStatus;
  progressPercent?: number;
  runtimeEngine?: string;
  runtimeId?: string;
  readRows?: number;
  writeRows?: number;
  startTime?: string;
  finishTime?: string;
  errorCode?: number;
  errorMessage?: string;
  definitionSnapshot?: DataSyncDefinitionSnapshot;
  createTime?: string;
  updateTime?: string;
}

export interface DataSyncInstancePageParams {
  pageNo: number;
  pageSize: number;
  taskId?: string;
  syncType?: DataSyncType;
  keyword?: string;
  status?: DataSyncInstanceStatus;
  activeOnly?: boolean;
  triggerType?: string;
  startTimeStart?: string;
  startTimeEnd?: string;
}

export interface DataSyncInstancePageResult {
  bizData: DataSyncInstanceRecord[];
  pagination: PaginationInfo;
}
