import { offlineIncremental, validIncrementalConfig } from "@/service/data-sync/parameters";
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
  startup?: DataSyncTaskSavePayload["startup"],
  transforms?: DataSyncTaskSavePayload["transforms"],
  pipelineOptions?: DataSyncTaskSavePayload["pipelineOptions"],
): DataSyncTaskSavePayload {
  const common = {
    name: form.name.trim(),
    debug: form.debug,
    sourceDataSourceId: form.source.dataSourceId,
    sourceDatabase: form.source.database || undefined,
    sourceSchema: form.source.schema || undefined,
    sourceTable: form.source.table,
    targetDataSourceId: form.sink.dataSourceId,
    targetDatabase: form.sink.database || undefined,
    targetSchema: form.sink.schema || undefined,
    targetTable: form.sink.table,
    remark: form.remark.trim() || undefined,
  };
  return {
    ...common,
    solutionType: draft.solutionType,
    sourceConnector: draft.sourceConnector,
    sinkConnector: draft.sinkConnector,
    sourceProducesChangelog: draft.sourceProducesChangelog,
    transforms: transforms ?? draft.form?.transforms,
    pipelineOptions: pipelineOptions ?? draft.form?.pipelineOptions,
    steps: draft.steps,
    ...(usesChangelog && startup ? { startup: { ...startup } } : {}),
    writeMode: form.sink.writeMode,
    targetPrimaryKeys: form.sink.targetPrimaryKeys?.filter(Boolean) ?? [],
    ...(!batch && offlineIncremental(draft.solutionType, draft.steps) && form.source.incremental
      ? { incremental: { ...form.source.incremental } }
      : {}),
    ...(usesChangelog && form.source.chunkKeyColumn && !batch
      ? { chunkKeyColumn: form.source.chunkKeyColumn }
      : {}),
    ...(usesChangelog
      ? {
          schemaChangesEnabled: form.sink.schemaChangesEnabled,
          allowDropColumns: form.sink.schemaChangesEnabled && form.sink.allowDropColumns,
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
  const continuous = draft.sourceProducesChangelog && draft.steps.includes("INCREMENTAL");
  return Boolean(
    !published &&
    Boolean(combination) &&
    !solutionsLoading &&
    !solutionsError &&
    combination!.writeModes.includes(form.sink.writeMode) &&
    (!(!batch && draft.sourceProducesChangelog) ||
      (form.source.sourceHasPrimaryKey !== undefined &&
        (form.source.sourceHasPrimaryKey
          ? form.sink.writeMode !== "RETRACT"
          : initialLoad &&
            (!continuous || draft.targetType === "MYSQL") &&
            !form.sink.schemaChangesEnabled &&
            Boolean(form.source.chunkKeyColumn.trim()) &&
            (form.sink.targetPrimaryKeys.length
              ? form.sink.writeMode === "UPSERT" && form.sink.targetPrimaryKeys.every(Boolean)
              : form.sink.writeMode === (continuous ? "RETRACT" : "APPEND"))))) &&
    (!useIncremental ||
      (batch
        ? mappings.every((mapping) => validIncrementalConfig(mapping.incremental, !initialLoad))
        : validIncrementalConfig(form.source.incremental, !initialLoad))) &&
    selectedSourceDataSource?.dbType === draft.sourceType &&
    selectedTargetDataSource?.dbType === draft.targetType &&
    (!supportsScheduling ||
      (!scheduleLoading &&
        !scheduleNeedsReload &&
        (!schedule?.enabled || sameScheduleRule(scheduleForm, schedule)))) &&
    form.name.trim() &&
    Boolean(form.source.dataSourceId && form.sink.dataSourceId) &&
    (!prepareSchema ||
      Boolean(form.sink.database && (draft.targetType !== "POSTGRE_SQL" || form.sink.schema))) &&
    (batch
      ? validTableMappings(
          mappings,
          targetCatalog.tables,
          combination?.writeModes || [],
          prepareSchema,
        )
      : Boolean(
          form.source.table &&
          (prepareSchema
            ? Boolean(form.sink.table.trim())
            : selectedTableKey(
                targetCatalog.tables,
                form.sink.database,
                form.sink.schema,
                form.sink.table,
              )),
        )) &&
    !sourceCatalog.loading &&
    !targetCatalog.loading &&
    !sourceCatalog.error &&
    !targetCatalog.error,
  );
}
