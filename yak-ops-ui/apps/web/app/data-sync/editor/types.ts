import type { DataSourceCatalogTable, DataSourceRecord } from "@/service/datasource";
import type {
  DataSyncDraft,
  DataSyncTaskRecord,
  DataSyncIncrementalConfig,
  DataSyncWriteMode,
  DataSyncTransform,
  DataSyncStartupOptions,
  DataSyncPipelineOptions,
  DataSyncTableSelection,
  DataSyncMappingTemplate,
  DataSyncSourceOptions,
  DataSyncTypeMapping,
} from "@/service/data-sync";

export interface EditorSource {
  dataSourceId: string;
  database: string;
  schema: string;
  table: string;
  incremental?: DataSyncIncrementalConfig;
  where: string;
  splitKey: string;
  options?: DataSyncSourceOptions;
}

export interface EditorSink {
  dataSourceId: string;
  database: string;
  schema: string;
  table: string;
  writeMode: DataSyncWriteMode;
  targetPrimaryKeys: string[];
  schemaChangesEnabled: boolean;
  allowDropColumns: boolean;
  preSql: string[];
  postSql: string[];
  batchSize?: number;
}

/** Authored editor state; replication table options live on their individual mappings. */
export interface EditorForm {
  tableScope?: "SINGLE" | "DATABASE";
  name: string;
  remark: string;
  debug: boolean;
  transforms: DataSyncTransform[];
  startup?: DataSyncStartupOptions;
  pipelineOptions?: DataSyncPipelineOptions;
  selection?: DataSyncTableSelection;
  mappingTemplate?: DataSyncMappingTemplate;
  selectionResolved?: boolean;
  typeMappings?: DataSyncTypeMapping[];
  source: EditorSource;
  sink: EditorSink;
}

export interface CatalogOptions {
  schemas: string[];
  tables: DataSourceCatalogTable[];
  loading: boolean;
  error: string;
  refresh: () => void;
}

export const EMPTY_FORM: EditorForm = {
  name: "",
  remark: "",
  debug: false,
  transforms: [],
  source: {
    dataSourceId: "",
    database: "",
    schema: "",
    table: "",
    incremental: undefined,
    where: "",
    splitKey: "",
  },
  sink: {
    dataSourceId: "",
    database: "",
    schema: "",
    table: "",
    writeMode: "APPEND",
    targetPrimaryKeys: [],
    schemaChangesEnabled: true,
    allowDropColumns: false,
    preSql: [],
    postSql: [],
  },
};

export interface TaskEditorInput {
  draft: DataSyncDraft;
  task?: DataSyncTaskRecord;
  connections?: DataSourceRecord[];
}
