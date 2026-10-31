import type { DataSourceCatalogTable, DataSourceRecord } from "@/service/datasource";
import type {
  DataSyncDraft,
  DataSyncTaskRecord,
  DataSyncIncrementalConfig,
  DataSyncWriteMode,
  DataSyncTransform,
  DataSyncStartupOptions,
  DataSyncPipelineOptions,
} from "@/service/data-sync";

export interface EditorSource {
  dataSourceId: string;
  database: string;
  schema: string;
  table: string;
  incremental?: DataSyncIncrementalConfig;
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
}

/** Authored editor state; replication table options live on their individual mappings. */
export interface EditorForm {
  name: string;
  remark: string;
  debug: boolean;
  transforms: DataSyncTransform[];
  startup?: DataSyncStartupOptions;
  pipelineOptions?: DataSyncPipelineOptions;
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
  },
};

export interface TaskEditorInput {
  draft: DataSyncDraft;
  task?: DataSyncTaskRecord;
  connections?: DataSourceRecord[];
}
