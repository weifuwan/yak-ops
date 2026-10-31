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
    source: {
      dataSourceId: value?.sourceDataSourceId || "",
      database: value?.sourceDatabase || "",
      schema: value?.sourceSchema || "",
      table: value?.sourceTable || "",
      incremental: useIncremental ? value?.incremental || EMPTY_INCREMENTAL : undefined,
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
        value?.writeMode === "RETRACT"
          ? "RETRACT"
          : value?.writeMode === "UPSERT" || continuous || useIncremental
            ? "UPSERT"
            : "APPEND",
      targetPrimaryKeys: value?.targetPrimaryKeys ?? [],
    },
  };
}
