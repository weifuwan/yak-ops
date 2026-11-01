import type { DataSyncDraft, DataSyncTaskRecord } from "@/service/data-sync";
import { offlineIncremental } from "@/service/data-sync/parameters";
import { EMPTY_INCREMENTAL } from "./incremental-fields";
import { EMPTY_FORM, type EditorForm } from "./types";

/** Restore authored rules and offsets while deriving defaults from the current read strategy. */
export function createEditorForm(draft: DataSyncDraft, task?: DataSyncTaskRecord): EditorForm {
  const useIncremental = offlineIncremental(draft.productType, draft.steps);
  const continuous = draft.sourceProducesChangelog && draft.steps.includes("INCREMENTAL");
  const value = task || draft.form;
  return {
    ...EMPTY_FORM,
    name: draft.name,
    remark: value?.remark || "",
    debug: value?.debug ?? false,
    transforms: value?.transforms ?? [],
    startup: value?.startup,
    pipelineOptions: value?.pipelineOptions,
    tableScope: value?.tableScope,
    selection: value?.selection ? structuredClone(value.selection) : undefined,
    mappingTemplate: value?.mappingTemplate ? structuredClone(value.mappingTemplate) : undefined,
    selectionResolved: !value?.selection || Boolean(task),
    typeMappings: value?.typeMappings ? structuredClone(value.typeMappings) : undefined,
    source: {
      dataSourceId: value?.sourceDataSourceId || "",
      database: value?.sourceDatabase || "",
      schema: value?.sourceSchema || "",
      table: value?.sourceTable || "",
      incremental: useIncremental ? value?.incremental || EMPTY_INCREMENTAL : undefined,
      where: value?.sourceRead?.where || "",
      splitKey: value?.sourceRead?.splitKey || "",
      options: value?.sourceOptions ? { ...value.sourceOptions } : undefined,
    },
    sink: {
      dataSourceId: value?.targetDataSourceId || "",
      database: value?.targetDatabase || "",
      schema: value?.targetSchema || "",
      table: value?.targetTable || "",
      schemaChangesEnabled:
        continuous && (value?.schemaChangesEnabled ?? draft.targetType !== "ORACLE"),
      allowDropColumns: continuous && (value?.allowDropColumns ?? false),
      writeMode:
        value?.writeMode === "RETRACT" || value?.writeMode === "REPLACE"
          ? value.writeMode
          : value?.writeMode === "UPSERT" || continuous || useIncremental
            ? "UPSERT"
            : "APPEND",
      targetPrimaryKeys: value?.targetPrimaryKeys ?? [],
      preSql: value?.preSql ? [...value.preSql] : [],
      postSql: value?.postSql ? [...value.postSql] : [],
      batchSize: value?.sinkBatchSize,
    },
  };
}
