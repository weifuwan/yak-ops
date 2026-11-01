import { offlineIncremental, validIncrementalConfig } from "@/service/data-sync/parameters";
import { transformEditingError } from "@/service/data-sync/transforms";
import {
  validateBatchSize,
  validateSourceOptions,
  validateSqlHooks,
  validateTypeMappings,
} from "@/service/data-sync/table-configuration";
import { validTableMappings } from "./table-selection";
import { sameScheduleRule } from "@/app/schedule/schedule-presentation";
import { selectedTableKey } from "./table-catalog";
import type {
  DataSyncDraft,
  DataSyncTaskSavePayload,
  DataSyncTableMapping,
  DataSyncWriteMode,
  DataSyncCapabilities,
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
    tableScope: form.tableScope,
    selection: replication ? form.selection : undefined,
    mappingTemplate: replication ? form.mappingTemplate : undefined,
    typeMappings: form.typeMappings,
    steps: draft.steps,
    ...(replication && form.startup ? { startup: { ...form.startup } } : {}),
    writeMode: form.sink.writeMode,
    targetPrimaryKeys: form.sink.targetPrimaryKeys?.filter(Boolean) ?? [],
    ...(!replication
      ? {
          sourceRead:
            form.source.where.trim() || form.source.splitKey.trim()
              ? {
                  where: form.source.where.trim() || undefined,
                  splitKey: form.source.splitKey.trim() || undefined,
                }
              : undefined,
          preSql: form.sink.preSql.length ? [...form.sink.preSql] : undefined,
          postSql: form.sink.postSql.length ? [...form.sink.postSql] : undefined,
          sourceOptions: form.source.options,
          sinkBatchSize: form.sink.batchSize,
        }
      : {}),
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
  combination?: { writeModes: DataSyncWriteMode[] };
  capabilities?: DataSyncCapabilities;
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

export function editorValidationIssue({
  combination,
  capabilities,
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
}: ValidationInput): EditorIssue | undefined {
  let extraIssue: EditorIssue | undefined;
  try {
    validateSourceOptions(form.source.options);
  } catch (failure) {
    extraIssue = { message: (failure as Error).message, location: "source" };
  }
  try {
    validateSqlHooks({
      mode: form.sink.writeMode,
      preSql: form.sink.preSql,
      postSql: form.sink.postSql,
    });
    validateBatchSize(form.sink.batchSize);
    validateTypeMappings(form.typeMappings);
  } catch (failure) {
    extraIssue = { message: (failure as Error).message, location: "target" };
  }
  if (form.selection) {
    if (!capabilities?.regexSelection)
      extraIssue = { message: "当前连接器不支持正则选表", location: "source" };
    else if (!form.selectionResolved)
      extraIssue = { message: "请先匹配并应用当前正则规则", location: "source" };
    else if (
      !draft.steps.includes("FULL") ||
      !draft.steps.includes("INCREMENTAL") ||
      (form.startup && form.startup.mode !== "INITIAL") ||
      !prepareSchema ||
      !form.sink.schemaChangesEnabled
    )
      extraIssue = {
        message: "正则选表需从全量开始持续同步，并创建目标表和同步结构变更",
        location: "source",
      };
  }
  const valid = Boolean(
    Boolean(combination) &&
    !solutionsLoading &&
    !solutionsError &&
    (replication || combination!.writeModes.includes(form.sink.writeMode)) &&
    !replicationError &&
    !extraIssue &&
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
          capabilities?.maxTables ?? 100,
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
  if (valid) return undefined;
  if (!form.name.trim()) return { message: "请输入任务名称", location: "properties" };
  if (solutionsLoading) return { message: "正在加载同步方案", location: "datasource" };
  if (solutionsError || !combination)
    return {
      message: solutionsError || "当前链路不支持所选同步方案",
      location: "advanced",
      tab: "parameters",
      parameterTab: "read",
    };
  if (
    !form.source.dataSourceId ||
    !form.sink.dataSourceId ||
    selectedSourceDataSource?.dbType !== draft.sourceType ||
    selectedTargetDataSource?.dbType !== draft.targetType
  )
    return { message: "请选择有效的来源和去向数据源", location: "datasource" };
  if (
    supportsScheduling &&
    (scheduleLoading ||
      scheduleNeedsReload ||
      (schedule?.enabled && !sameScheduleRule(scheduleForm, schedule)))
  )
    return {
      message: scheduleNeedsReload
        ? "请先重新加载调度状态"
        : scheduleLoading
          ? "正在加载调度"
          : "请先停用调度再修改 Cron",
      location: "advanced",
      tab: "parameters",
      parameterTab: "runtime",
    };
  if (sourceCatalog.loading || sourceCatalog.error)
    return { message: sourceCatalog.error || "正在加载来源表", location: "source" };
  if (targetCatalog.loading || targetCatalog.error)
    return { message: targetCatalog.error || "正在加载目标表", location: "target" };
  if (extraIssue) return extraIssue;
  if (useIncremental && !validIncrementalConfig(form.source.incremental, !initialLoad))
    return { message: "请完善增量字段和初始起点", location: "source" };
  if (transformEditingError(form.transforms))
    return {
      message: transformEditingError(form.transforms) || "转换规则无效",
      location: replication ? "target" : "field-mapping",
    };
  if (!replication && !combination.writeModes.includes(form.sink.writeMode))
    return {
      message: "请选择当前策略支持的写入方式",
      location: "target",
    };
  if (replicationError) return { message: replicationError, location: "target" };
  return {
    message: replication && !mappings.length ? "请选择来源表" : "请完善目标表和表映射",
    location: replication && !mappings.length ? "source" : "target",
  };
}

export interface EditorIssue {
  message: string;
  location: "properties" | "advanced" | "datasource" | "source" | "target" | "field-mapping";
  tab?: "parameters" | "ddl" | "engine";
  parameterTab?: "read" | "write" | "runtime";
}

export const canSaveEditor = (input: ValidationInput) => !editorValidationIssue(input);
