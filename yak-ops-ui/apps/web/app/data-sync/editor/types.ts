import type { DataSourceCatalogTable, DataSourceRecord } from "@/service/datasource";
import type {
  DataSyncDraft,
  DataSyncTaskRecord,
  DataSyncIncrementalConfig,
  DataSyncWriteMode,
} from "@/service/data-sync";

export interface EditorForm {
  name: string;
  remark: string;
  debug: boolean;
  schemaChangesEnabled: boolean;
  allowDropColumns: boolean;
  writeMode: DataSyncWriteMode;
  keylessEnabled: boolean;
  chunkKeyColumn: string;
  incremental?: DataSyncIncrementalConfig;
  sourceDataSourceId: string;
  sourceDatabase: string;
  sourceSchema: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase: string;
  targetSchema: string;
  targetTable: string;
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
  schemaChangesEnabled: true,
  allowDropColumns: false,
  writeMode: "APPEND",
  keylessEnabled: false,
  chunkKeyColumn: "",
  incremental: undefined,
  sourceDataSourceId: "",
  sourceDatabase: "",
  sourceSchema: "",
  sourceTable: "",
  targetDataSourceId: "",
  targetDatabase: "",
  targetSchema: "",
  targetTable: "",
};

export interface TaskEditorInput {
  draft: DataSyncDraft;
  task?: DataSyncTaskRecord;
  connections?: DataSourceRecord[];
}
