import { validIncrementalConfig } from "@/service/data-sync/parameters";
import { validTableMappings } from "./table-mapping-editor";
import { sameScheduleRule } from "@/app/schedule/schedule-presentation";
import { selectedTableKey } from "./table-section";
import type {
  DataSyncDraft,
  DataSyncTaskSavePayload,
  DataSyncTableMapping,
  DataSyncWriteMode,
} from "@/service/data-sync";
import type { ScheduleRecord, ScheduleTimeRule } from "@/service/schedule";
import type { DataSourceRecord } from "@/service/datasource";
import type { CatalogOptions, EditorForm } from "./types";

export function buildEditorPayload(
  form: EditorForm,
  draft: DataSyncDraft,
  batch: boolean,
  mappings: DataSyncTableMapping[],
  usesChangelog: boolean,
): DataSyncTaskSavePayload {
  const common = {
    name: form.name.trim(),
    debug: form.debug,
    sourceDataSourceId: form.sourceDataSourceId,
    sourceDatabase: form.sourceDatabase || undefined,
    sourceSchema: form.sourceSchema || undefined,
    sourceTable: form.sourceTable,
    targetDataSourceId: form.targetDataSourceId,
    targetDatabase: form.targetDatabase || undefined,
    targetSchema: form.targetSchema || undefined,
    targetTable: form.targetTable,
    remark: form.remark.trim() || undefined,
  };
  return {
    ...common,
    solutionType: draft.solutionType,
    steps: draft.steps,
    writeMode: form.writeMode,
    targetPrimaryKeys: form.targetPrimaryKeys?.filter(Boolean) ?? [],
    ...(usesChangelog && form.keylessEnabled && !batch
      ? { keylessEnabled: true, chunkKeyColumn: form.chunkKeyColumn }
      : {}),
    ...(usesChangelog
      ? {
          schemaChangesEnabled: form.schemaChangesEnabled,
          allowDropColumns: form.schemaChangesEnabled && form.allowDropColumns,
        }
      : {}),
    tableMappings: batch ? mappings : undefined,
  };
}

interface ValidationInput {
  published: boolean;
  combination?: { writeModes: DataSyncWriteMode[] };
  solutionsLoading: boolean;
  solutionsError: string;
  useIncremental: boolean;
  initialLoad: boolean;
  batch: boolean;
  mappings: DataSyncTableMapping[];
  selectedSourceDataSource?: DataSourceRecord;
  selectedTargetDataSource?: DataSourceRecord;
  draft: DataSyncDraft;
  supportsScheduling: boolean;
  scheduleLoading: boolean;
  scheduleNeedsReload: boolean;
  schedule?: ScheduleRecord | null;
  scheduleForm: ScheduleTimeRule;
  form: EditorForm;
  prepareSchema: boolean;
  targetCatalog: CatalogOptions;
  sourceCatalog: CatalogOptions;
}

export function canSaveEditor({
  published,
  combination,
  solutionsLoading,
  solutionsError,
  useIncremental,
  initialLoad,
  batch,
  mappings,
  selectedSourceDataSource,
  selectedTargetDataSource,
  draft,
  supportsScheduling,
  scheduleLoading,
  scheduleNeedsReload,
  schedule,
  scheduleForm,
  form,
  prepareSchema,
  targetCatalog,
  sourceCatalog,
}: ValidationInput): boolean {
  return Boolean(
    !published &&
    Boolean(combination) &&
    !solutionsLoading &&
    !solutionsError &&
    combination!.writeModes.includes(form.writeMode) &&
    (form.keylessEnabled
      ? !batch &&
        draft.solutionType === "SINGLE_TABLE_REALTIME" &&
        draft.targetType === "MYSQL" &&
        !form.schemaChangesEnabled &&
        (form.targetPrimaryKeys?.length
          ? form.writeMode === "UPSERT" && form.targetPrimaryKeys.every(Boolean)
          : form.writeMode === "RETRACT") &&
        Boolean(form.chunkKeyColumn.trim())
      : form.writeMode !== "RETRACT") &&
    (!useIncremental ||
      (batch
        ? mappings.every((mapping) => validIncrementalConfig(mapping.incremental, !initialLoad))
        : validIncrementalConfig(form.incremental, !initialLoad))) &&
    selectedSourceDataSource?.dbType === draft.sourceType &&
    selectedTargetDataSource?.dbType === draft.targetType &&
    (!supportsScheduling ||
      (!scheduleLoading &&
        !scheduleNeedsReload &&
        (!schedule?.enabled || sameScheduleRule(scheduleForm, schedule)))) &&
    form.name.trim() &&
    Boolean(form.sourceDataSourceId && form.targetDataSourceId) &&
    (!prepareSchema ||
      Boolean(form.targetDatabase && (draft.targetType !== "POSTGRE_SQL" || form.targetSchema))) &&
    (batch
      ? validTableMappings(
          mappings,
          targetCatalog.tables,
          combination?.writeModes || [],
          prepareSchema,
        )
      : Boolean(
          form.sourceTable &&
          (prepareSchema
            ? Boolean(form.targetTable.trim())
            : selectedTableKey(
                targetCatalog.tables,
                form.targetDatabase,
                form.targetSchema,
                form.targetTable,
              )),
        )) &&
    !sourceCatalog.loading &&
    !targetCatalog.loading &&
    !sourceCatalog.error &&
    !targetCatalog.error,
  );
}
