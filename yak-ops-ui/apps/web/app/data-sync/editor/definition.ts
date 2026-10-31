import { offlineIncremental, validIncrementalConfig } from "@/service/data-sync/parameters";
import { transformEditingError } from "@/service/data-sync/transforms";
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
  replication: boolean,
  mappings: DataSyncTableMapping[],
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
    productType: draft.productType,
    sourceConnector: draft.sourceConnector,
    sinkConnector: draft.sinkConnector,
    sourceProducesChangelog: draft.sourceProducesChangelog,
    transforms: form.transforms,
    pipelineOptions: form.pipelineOptions,
    steps: draft.steps,
    ...(replication && form.startup ? { startup: { ...form.startup } } : {}),
    writeMode: form.sink.writeMode,
    targetPrimaryKeys: form.sink.targetPrimaryKeys?.filter(Boolean) ?? [],
    ...(!replication &&
    offlineIncremental(draft.productType, draft.steps) &&
    form.source.incremental
      ? { incremental: { ...form.source.incremental } }
      : {}),
    ...(replication
      ? {
          schemaChangesEnabled: form.sink.schemaChangesEnabled,
          allowDropColumns: form.sink.schemaChangesEnabled && form.sink.allowDropColumns,
        }
      : {}),
    tableMappings: replication ? mappings : undefined,
  };
}

interface ValidationInput {
  published: boolean;
  combination?: { writeModes: DataSyncWriteMode[] };
  solutionsLoading: boolean;
  solutionsError: string;
  useIncremental: boolean;
  initialLoad: boolean;
  replication: boolean;
  mappings: DataSyncTableMapping[];
  selectedSourceDataSource?: DataSourceRecord;
  selectedTargetDataSource?: DataSourceRecord;
  draft: DataSyncDraft;
  replicationError: string;
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
  replication,
  mappings,
  selectedSourceDataSource,
  selectedTargetDataSource,
  draft,
  replicationError,
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
    (replication || combination!.writeModes.includes(form.sink.writeMode)) &&
    !replicationError &&
    !transformEditingError(form.transforms) &&
    (!useIncremental || validIncrementalConfig(form.source.incremental, !initialLoad)) &&
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
    (replication
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
