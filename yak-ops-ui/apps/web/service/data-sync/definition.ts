import type {
  DataSyncTaskPageParams,
  DataSyncTaskPageResult,
  DataSyncTaskRecord,
  DataSyncTaskSavePayload,
  DataSyncTaskUpdatePayload,
  DataSyncParameters,
  DataSyncTableMapping,
  TaskDefinitionRecord,
  TaskDefinitionPageResult,
} from "./types";
import {
  displaySyncSteps,
  pipelineSolutionType,
  parseSyncParameters,
  solutionSyncType,
  syncTableMappings,
  supportsMultipleTables,
  validateSyncParameters,
} from "./parameters.ts";

/** Writes the single public product configuration; connector identifiers are resolved by the backend. */
export function buildSyncParameters(task: DataSyncTaskSavePayload): DataSyncParameters {
  const mappings: DataSyncTableMapping[] = supportsMultipleTables(task.solutionType)
    ? (task.tableMappings ?? [])
    : [
        {
          source: {
            database: task.sourceDatabase,
            schema: task.sourceSchema,
            table: task.sourceTable,
            ...(task.chunkKeyColumn ? { chunkKeyColumn: task.chunkKeyColumn } : {}),
          },
          target: {
            database: task.targetDatabase,
            schema: task.targetSchema,
            table: task.targetTable,
            ...(task.targetPrimaryKeys?.length ? { primaryKeys: [...task.targetPrimaryKeys] } : {}),
          },
          writeMode: task.writeMode,
          ...(task.incremental ? { incremental: { ...task.incremental } } : {}),
        },
      ];
  if (!mappings.length) throw new Error("请选择同步表");
  const realtime = solutionSyncType(task.solutionType) === "REALTIME";
  const continuous = realtime && task.steps.includes("INCREMENTAL");
  const evolution = continuous && (task.schemaChangesEnabled ?? true);
  const dataSteps = (["FULL", "INCREMENTAL"] as const).filter((step) => task.steps.includes(step));
  const physicalPath = (table: { database?: string; schema?: string; table: string }) =>
    [table.database, table.schema, table.table].filter(Boolean).join(".");
  const sourceTables = mappings.map((mapping) => ({
    database: mapping.source.database,
    schema: mapping.source.schema,
    table: mapping.source.table,
    ...(mapping.source.chunkKeyColumn
      ? { snapshot: { chunkKeyColumn: mapping.source.chunkKeyColumn } }
      : {}),
    ...(mapping.incremental ? { incremental: { ...mapping.incremental } } : {}),
  }));
  const sinkTables = mappings.map((mapping) => ({
    database: mapping.target.database,
    schema: mapping.target.schema,
    table: mapping.target.table,
    write: {
      mode: mapping.writeMode,
      ...(mapping.target.primaryKeys?.length
        ? { primaryKeys: [...mapping.target.primaryKeys] }
        : {}),
      ...(mapping.writeMode === "RETRACT"
        ? { transaction: { mode: "CHECKPOINT_XA" as const } }
        : {}),
    },
  }));
  return {
    source: {
      type: realtime ? ("MYSQL_CDC" as const) : ("JDBC" as const),
      datasourceId: task.sourceDataSourceId,
      ...(realtime
        ? {
            // Retain the full V6 JSON offset when editing: steps alone cannot identify
            // EARLIEST_OFFSET, SPECIFIC_OFFSETS or TIMESTAMP (or their parameters).
            startup: task.startup
              ? { ...task.startup }
              : {
                  mode: task.steps.includes("FULL")
                    ? task.steps.includes("INCREMENTAL")
                      ? ("INITIAL" as const)
                      : ("SNAPSHOT" as const)
                    : ("LATEST_OFFSET" as const),
                },
          }
        : {
            syncMode:
              dataSteps.length === 2
                ? ("FULL_AND_INCREMENTAL" as const)
                : dataSteps.includes("INCREMENTAL")
                  ? ("INCREMENTAL" as const)
                  : ("FULL" as const),
          }),
      tables: sourceTables,
    },
    sink: {
      type: "JDBC" as const,
      datasourceId: task.targetDataSourceId,
      schemaPolicy: {
        initial: task.steps.includes("SCHEMA") ? ("CREATE_OR_ALTER" as const) : ("VERIFY" as const),
        evolution: {
          enabled: evolution,
          allowDropColumns: Boolean(evolution && task.allowDropColumns),
        },
      },
      tables: sinkTables,
    },
    route: mappings.map((mapping) => ({
      "source-table": physicalPath(mapping.source),
      "sink-table": physicalPath(mapping.target),
    })),
    transform: [] as [],
    pipeline: {
      "execution.runtime-mode": continuous ? ("STREAMING" as const) : ("BATCH" as const),
      "pipeline.table-scope": supportsMultipleTables(task.solutionType)
        ? ("DATABASE" as const)
        : ("SINGLE" as const),
    },
  };
}

export function createDefinitionRequest(task: DataSyncTaskSavePayload) {
  return {
    name: task.name,
    taskType: "DATA_SYNC",
    debug: task.debug,
    remark: task.remark,
    parameters: buildSyncParameters(task),
  };
}

/** Omitting parameters edits metadata only and preserves the existing immutable parameter version. */
export function updateDefinitionRequest(task: DataSyncTaskUpdatePayload) {
  const request: {
    name: string;
    remark?: string;
    parameters?: DataSyncParameters;
    debug?: boolean;
  } = {
    name: task.name,
    remark: task.remark,
    debug: task.debug,
  };
  if ("solutionType" in task) request.parameters = buildSyncParameters(task);
  return request;
}

export function definitionPageRequest(params: DataSyncTaskPageParams) {
  const parameterFilters: Record<string, string> = {};
  if (params.solutionType) parameterFilters.solutionType = params.solutionType;
  if (params.syncType === "OFFLINE") parameterFilters.sourceType = "JDBC";
  if (params.syncType === "REALTIME") parameterFilters.sourceType = "MYSQL_CDC";
  if (params.sourceDataSourceId) parameterFilters.sourceDataSourceId = params.sourceDataSourceId;
  if (params.targetDataSourceId) parameterFilters.targetDataSourceId = params.targetDataSourceId;
  return {
    pageNo: params.pageNo,
    pageSize: params.pageSize,
    keyword: params.keyword,
    status: params.status,
    taskType: "DATA_SYNC",
    parameterFilters,
  };
}

/** Projects one immutable V6 JSON definition into the editor's transient UI state. */
export function toSyncTaskRecord(definition: TaskDefinitionRecord): DataSyncTaskRecord {
  if (definition.taskType !== "DATA_SYNC") throw new Error("不支持的同步任务定义");
  const parameters = parseSyncParameters(definition.parameterSchemaVersion, definition.parameters);
  let configurationError: string | undefined;
  try {
    validateSyncParameters(parameters);
  } catch (failure) {
    configurationError = failure instanceof Error ? failure.message : "任务参数校验失败";
  }
  const tableMappings = syncTableMappings(parameters);
  const mapping = tableMappings[0];
  return {
    id: definition.id,
    ...(configurationError ? { configurationError } : {}),
    tableMappings,

    name: definition.name,
    solutionType: pipelineSolutionType(parameters),
    steps: displaySyncSteps(parameters),
    ...(parameters.source.startup ? { startup: { ...parameters.source.startup } } : {}),
    schemaChangesEnabled: parameters.sink.schemaPolicy.evolution.enabled,
    allowDropColumns: parameters.sink.schemaPolicy.evolution.allowDropColumns,
    syncType: solutionSyncType(pipelineSolutionType(parameters)),
    status: definition.status,
    debug: definition.debug ?? false,
    writeMode: mapping.writeMode,
    chunkKeyColumn: mapping.source.chunkKeyColumn,
    targetPrimaryKeys: mapping.target.primaryKeys,
    incremental: mapping.incremental ? { ...mapping.incremental } : undefined,
    sourceDataSourceId: parameters.source.datasourceId,
    sourceDatabase: mapping.source.database ?? undefined,
    sourceSchema: mapping.source.schema ?? undefined,
    sourceTable: mapping.source.table,
    targetDataSourceId: parameters.sink.datasourceId,
    targetDatabase: mapping.target.database ?? undefined,
    targetSchema: mapping.target.schema ?? undefined,
    targetTable: mapping.target.table,
    definitionVersion: definition.definitionVersion,
    remark: definition.remark,
    createTime: definition.createTime,
    updateTime: definition.updateTime,
    updateBy: definition.updateBy,
  };
}

/**
 * A single unreadable stored definition must not turn a successful page request
 * into an empty/error page. Keep its identity visible; do not invent executable
 * parameters or silently interpret an older transaction contract as XA.
 */
export function projectSyncTaskPage(page: TaskDefinitionPageResult): DataSyncTaskPageResult {
  return {
    ...page,
    bizData: page.bizData.map((definition) => {
      try {
        return toSyncTaskRecord(definition);
      } catch (error) {
        return {
          id: definition.id,
          name: definition.name,
          status: definition.status,
          definitionVersion: definition.definitionVersion,
          updateBy: definition.updateBy,
          updateTime: definition.updateTime,
          configurationError:
            error instanceof SyntaxError
              ? "任务参数 JSON 格式错误"
              : error instanceof Error
                ? error.message
                : "任务参数无法解析",
        };
      }
    }),
  };
}
