import type { TaskInstanceStatus } from "../task-execution/types";

export type DataSyncWriteMode = "APPEND" | "UPSERT" | "REPLACE" | "RETRACT";

export type DataSyncDmlEvent = "INSERT" | "UPDATE" | "DELETE";

export interface DataSyncTableRead {
  where?: string;
  splitKey?: string;
}

export interface DataSyncSourceOptions {
  fetchSize?: number;
  queryTimeoutSeconds?: number;
}

export interface DataSyncTypeMapping {
  sourceType: string;
  targetType: string;
}

export interface DataSyncTableSnapshot {
  chunkKeyColumn?: string;
  skip?: boolean;
  where?: string;
}

export interface DataSyncDml {
  events: DataSyncDmlEvent[];
}

export interface DataSyncTableSelection {
  mode: "REGEX";
  databasePattern: string;
  tablePattern: string;
  snapshot?: DataSyncTableSnapshot;
  dml?: DataSyncDml;
}

export interface DataSyncTableWrite {
  mode: DataSyncWriteMode;
  primaryKeys?: string[];
  transaction?: { mode: "CHECKPOINT_XA" };
  preSql?: string[];
  postSql?: string[];
  batchSize?: number;
}

export interface DataSyncMappingTemplate {
  database?: string;
  schema?: string;
  table: string;
  write: DataSyncTableWrite;
}

export type DataSyncType = "OFFLINE" | "REALTIME";

export type DataSyncProductType = "SINGLE_TABLE_BATCH" | "DATABASE_REPLICATION";
export type DataSyncStep = "SCHEMA" | "FULL" | "INCREMENTAL";

export interface DataSyncCapabilities {
  maxTables: number;
  sourceRead: boolean;
  explicitSplitKey: boolean;
  dataPreview: boolean;
  sqlHooks: boolean;
  regexSelection: boolean;
  snapshotFilter: boolean;
  snapshotSkip: boolean;
  dmlEvents: boolean;
  typeMapping: boolean;
  targetStructureEditor: boolean; // Support for editing target table structure
  partitionFields: boolean; // Support for partition field configuration
  incrementalStream: boolean; // Support for incremental stream format
  writeModes: DataSyncWriteMode[];
  sourceEngineKeys: string[];
  sinkEngineKeys: string[];
  typeMappingRules: Array<{ sourceType: string; targetTypes: string[]; conditional: boolean }>;
}

export interface DataSyncSolution {
  id: string;
  sourceConnector: string;
  sinkConnector: string;
  producesChangelog: boolean;
  productType: DataSyncProductType;
  displayName: string;
  capabilities: DataSyncCapabilities;
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

/** Partition field assignment configuration. */
export type DataSyncPartitionAssignment =
  | { type: "CONSTANT"; value: string }
  | { type: "SOURCE_FIELD"; field: string; valueType: "ENUM" }
  | {
      type: "SOURCE_FIELD";
      field: string;
      valueType: "TIMESTAMP";
      sourceFormat:
        | "TIME_STRING"
        | "TIME_OBJECT"
        | "UNIX_TIMESTAMP_SECONDS"
        | "UNIX_TIMESTAMP_MILLIS";
      sourcePattern?: string; // For TIME_STRING
      targetFormat: string; // Target partition format like "yyyyMMdd"
    }
  | {
      type: "EVENT_TIME";
      sourceFormat:
        | "TIME_STRING"
        | "TIME_OBJECT"
        | "UNIX_TIMESTAMP_SECONDS"
        | "UNIX_TIMESTAMP_MILLIS";
      targetFormat: string;
    };

/** Target table structure definition for schema creation. */
export interface DataSyncTargetColumn {
  name: string;
  type: string;
  nullable?: boolean;
  defaultValue?: string;
  comment?: string;
}

export interface DataSyncTargetPartition {
  name: string;
  assignment: DataSyncPartitionAssignment;
}

export interface DataSyncTargetTableStructure {
  columns: DataSyncTargetColumn[];
  partitions?: DataSyncTargetPartition[];
  tableType?: "NORMAL" | "PK_DELTA" | "APPEND_DELTA"; // For MaxCompute
  tableProperties?: Record<string, string>;
}

/** Incremental stream metadata format (增量流水表格式). */
export interface DataSyncIncrementalStreamMetadata {
  sequenceId: string; // sequence_id
  operationType: string; // operation_type: I/D/U or DDL
  executeTime: string; // execute_time
  beforeImage: string; // before_image: Y/N
  afterImage: string; // after_image: Y/N
  srcDatasource: string; // src_datasource
  srcDatabase: string; // src_database
  srcTable: string; // src_table
  ddlSql?: string; // ddl_sql (for DDL operations)
}

export type DataSyncIncrementalStreamFormat = "FLAT_COLUMNS" | "JSON_COLUMNS";

export interface DataSyncIncrementalConfig {
  mode: "TIMESTAMP" | "INCREASING";
  column: string;
  initialValue?: string;
  delaySeconds?: number;
  lookbackSeconds?: number;
}

export interface DataSyncTableMapping {
  source: DataSyncTablePath & {
    read?: DataSyncTableRead;
    options?: DataSyncSourceOptions;
    snapshot?: DataSyncTableSnapshot;
    dml?: DataSyncDml;
  };
  target: DataSyncTablePath & {
    primaryKeys?: string[];
    structure?: DataSyncTargetTableStructure; // For prepareSchema mode
  };
  writeMode: DataSyncWriteMode;
  preSql?: string[];
  postSql?: string[];
  batchSize?: number;
  incremental?: DataSyncIncrementalConfig;
  incrementalStreamFormat?: DataSyncIncrementalStreamFormat; // For incremental stream mode
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

/** Transient editor projection. It is never sent to the API or persisted as task parameters. */
export interface DataSyncParameters {
  source: {
    type: string;
    datasourceId: string;
    selection?: DataSyncTableSelection;
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
        read?: DataSyncTableRead;
        options?: DataSyncSourceOptions;
        snapshot?: DataSyncTableSnapshot;
        dml?: DataSyncDml;
        incremental?: DataSyncIncrementalConfig;
      }
    >;
  };
  sink: {
    type: string;
    datasourceId: string;
    mapping?: DataSyncMappingTemplate;
    typeMappings?: DataSyncTypeMapping[];
    schemaPolicy: {
      initial: "VERIFY" | "CREATE_OR_ALTER";
      evolution: { enabled: boolean; allowDropColumns: boolean };
    };
    tables: Array<
      DataSyncTablePath & {
        write: DataSyncTableWrite;
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

export type DataSyncTaskStatus = "OFFLINE" | "ONLINE";

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
  sourceRead?: DataSyncTableRead;
  sourceOptions?: DataSyncSourceOptions;
  sinkBatchSize?: number;
  typeMappings?: DataSyncTypeMapping[];
  selection?: DataSyncTableSelection;
  mappingTemplate?: DataSyncMappingTemplate;
  preSql?: string[];
  postSql?: string[];
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
  archived?: boolean;
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
  archived?: boolean;
  definitionVersion: number;
  revision: number;
  publishedVersion: number | null;
  updateBy?: string;
  updateTime?: string;
  configurationError: string;
}

export type DataSyncTaskListRecord = DataSyncTaskRecord | DataSyncUnreadableTaskRecord;

/** Flink CDC Pipeline YAML represented as a JSON mapping for the task API. */
export interface DataSyncPipelineDefinition {
  source: Record<string, unknown> & {
    type: string;
    "datasource-id": string;
    tables: string;
  };
  sink: Record<string, unknown> & {
    type: string;
    "datasource-id": string;
  };
  route: Array<Record<string, unknown> & {
    "source-table": string;
    "sink-table": string;
  }>;
  pipeline: Record<string, unknown> & {
    "execution.runtime-mode": "BATCH" | "STREAMING";
  };
  transform?: Array<Record<string, unknown>>;
}

export interface DataSyncPipelineTaskParameters {
  definition: DataSyncPipelineDefinition;
}

export interface TaskDefinitionRecord {
  id: string;
  name: string;
  taskType: string;
  status: DataSyncTaskStatus | string;
  archived?: boolean;
  definitionVersion: number;
  revision: number;
  publishedVersion: number | null;
  parameters: DataSyncPipelineTaskParameters;
  remark?: string;
  createTime?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface TaskDefinitionVersionSummary {
  definitionId: string;
  version: number;
  createBy: string;
  createTime: string;
}

/** Historical definitions use the same Pipeline contract as the current task. */
export interface TaskDefinitionVersion extends TaskDefinitionVersionSummary {
  parameters: DataSyncPipelineTaskParameters;
}

export interface PublishTaskRequest {
  version: number;
  expectedRevision: number;
}

interface DataSyncTaskSaveBase {
  name: string;
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
  sourceRead?: DataSyncTableRead;
  sourceOptions?: DataSyncSourceOptions;
  sinkBatchSize?: number;
  typeMappings?: DataSyncTypeMapping[];
  selection?: DataSyncTableSelection;
  mappingTemplate?: DataSyncMappingTemplate;
  preSql?: string[];
  postSql?: string[];
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
  | Pick<DataSyncTaskSaveBase, "name" | "remark">;

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
  triggerType?: string;
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

export interface DataSyncInstanceSummary {
  id: string;
  taskId: string;
  taskName: string;
  taskVersion: number;
  syncType: "REALTIME";
  triggerType?: string;
  status: DataSyncInstanceStatus;
  startTime?: string;
  finishTime?: string;
  createTime?: string;
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

