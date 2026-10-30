import type {
  DataSyncParameters,
  DataSyncIncrementalConfig,
  DataSyncSolutionType,
  DataSyncStep,
  DataSyncTableMapping,
  DataSyncTablePath,
} from "./types";

const WRITES = new Set(["APPEND", "UPSERT", "RETRACT"]);
const STARTUP = new Set([
  "INITIAL",
  "SNAPSHOT",
  "EARLIEST_OFFSET",
  "LATEST_OFFSET",
  "SPECIFIC_OFFSETS",
  "TIMESTAMP",
]);
const hasOnlyKeys = (value: object, keys: string[]) =>
  Object.keys(value).every((key) => keys.includes(key));
const path = (table: DataSyncTablePath) =>
  [table.database, table.schema, table.table].filter(Boolean).join(".");

export const solutionSyncType = (type: DataSyncSolutionType) =>
  type === "SINGLE_TABLE_REALTIME" || type === "DATABASE_REALTIME" ? "REALTIME" : "OFFLINE";
export const supportsMultipleTables = (type: DataSyncSolutionType) =>
  type === "DATABASE_OFFLINE" || type === "DATABASE_REALTIME";
export const offlineIncremental = (solution: DataSyncSolutionType, steps: DataSyncStep[]) =>
  solutionSyncType(solution) === "OFFLINE" && steps.includes("INCREMENTAL");

/** Product category is inferred from the JSON source and table scope. */
export function pipelineSolutionType(value: DataSyncParameters): DataSyncSolutionType {
  const multiple =
    value.pipeline?.["pipeline.table-scope"] === "DATABASE" ||
    (value.pipeline?.["pipeline.table-scope"] === undefined && value.source.tables.length > 1);
  const realtime = value.source.type === "MYSQL_CDC";
  return realtime
    ? multiple
      ? "DATABASE_REALTIME"
      : "SINGLE_TABLE_REALTIME"
    : multiple
      ? "DATABASE_OFFLINE"
      : "SINGLE_TABLE_OFFLINE";
}

export const validIncrementalConfig = (
  value: DataSyncIncrementalConfig | undefined,
  requireStart: boolean,
) => {
  if (
    !value ||
    !["TIMESTAMP", "INCREASING"].includes(value.mode) ||
    !value.column?.trim() ||
    value.column.length > 128
  )
    return false;
  if (requireStart && !value.initialValue?.trim()) return false;
  if (
    value.initialValue &&
    (value.mode === "INCREASING"
      ? !/^-?\d+$/.test(value.initialValue)
      : !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/.test(value.initialValue))
  )
    return false;
  if (
    [value.delaySeconds, value.lookbackSeconds].some(
      (v) => v !== undefined && (!Number.isSafeInteger(v) || v < 0),
    )
  )
    return false;
  if (value.mode === "INCREASING" && (value.delaySeconds || value.lookbackSeconds)) return false;
  return true;
};

/** Parse the persisted V6 JSON without converting YAML in the frontend. */
export function parseSyncParameters(schemaVersion: number, content: unknown): DataSyncParameters {
  if (schemaVersion !== 6) throw new Error("不支持的同步任务参数版本");
  if (!content || typeof content !== "object" || Array.isArray(content))
    throw new Error("无效的 V6 JSON Pipeline 定义");
  const { index, ...definition } = content as DataSyncParameters & { index?: unknown };
  const value = definition as DataSyncParameters;
  if (!hasOnlyKeys(value, ["source", "sink", "route", "transform", "pipeline"]))
    throw new Error("Pipeline 包含未识别的配置项");
  validateSyncParameters(value);
  return value;
}

export function syncTableMappings(value: DataSyncParameters): DataSyncTableMapping[] {
  const sources = new Map(value.source.tables.map((table) => [path(table), table]));
  const sinks = new Map(value.sink.tables.map((table) => [path(table), table]));
  return value.route.map((route) => {
    const table = sources.get(route["source-table"]);
    const target = sinks.get(route["sink-table"]);
    if (!table || !target) throw new Error("找不到 Pipeline 表映射");
    return {
      source: {
        database: table.database,
        schema: table.schema,
        table: table.table,
        ...(table.snapshot ? { chunkKeyColumn: table.snapshot.chunkKeyColumn } : {}),
      },
      target: {
        database: target.database,
        schema: target.schema,
        table: target.table,
        ...(target.write.primaryKeys ? { primaryKeys: target.write.primaryKeys } : {}),
      },
      writeMode: target.write.mode,
      ...(table.incremental ? { incremental: table.incremental } : {}),
    };
  });
}

export function displaySyncSteps(value: DataSyncParameters): DataSyncStep[] {
  const dataSteps: DataSyncStep[] =
    value.source.type === "MYSQL_CDC"
      ? value.source.startup?.mode === "SNAPSHOT"
        ? ["FULL"]
        : value.source.startup?.mode === undefined || value.source.startup.mode === "INITIAL"
          ? ["FULL", "INCREMENTAL"]
          : ["INCREMENTAL"]
      : value.source.syncMode === "INCREMENTAL"
        ? ["INCREMENTAL"]
        : value.source.syncMode === "FULL_AND_INCREMENTAL"
          ? ["FULL", "INCREMENTAL"]
          : ["FULL"];
  return [
    ...(value.sink.schemaPolicy.initial === "CREATE_OR_ALTER" ? (["SCHEMA"] as const) : []),
    ...dataSteps,
  ];
}

export function validateSyncParameters(value: DataSyncParameters): void {
  if (
    !value?.source ||
    !value.sink ||
    !hasOnlyKeys(value.source, ["type", "datasourceId", "syncMode", "startup", "tables"]) ||
    !hasOnlyKeys(value.sink, ["type", "datasourceId", "schemaPolicy", "tables"]) ||
    !["JDBC", "MYSQL_CDC"].includes(value.source.type) ||
    value.sink.type !== "JDBC" ||
    !value.source.datasourceId ||
    !value.sink.datasourceId ||
    !Array.isArray(value.source.tables) ||
    !Array.isArray(value.sink.tables) ||
    !Array.isArray(value.route) ||
    !Array.isArray(value.transform) ||
    value.transform.length !== 0 ||
    !value.source.tables.length ||
    value.source.tables.length > 100 ||
    value.source.tables.length !== value.sink.tables.length ||
    value.route.length !== value.source.tables.length ||
    (value.pipeline !== undefined &&
      (!hasOnlyKeys(value.pipeline, ["execution.runtime-mode", "pipeline.table-scope"]) ||
        (value.pipeline["pipeline.table-scope"] !== undefined &&
          !["SINGLE", "DATABASE"].includes(value.pipeline["pipeline.table-scope"])) ||
        (value.pipeline["pipeline.table-scope"] === "SINGLE" && value.source.tables.length !== 1) ||
        (value.pipeline["execution.runtime-mode"] !== undefined &&
          !["BATCH", "STREAMING"].includes(value.pipeline["execution.runtime-mode"]))))
  ) {
    throw new Error("不支持的 V6 Pipeline 定义");
  }
  const realtime = value.source.type === "MYSQL_CDC";
  const sourceKeys = ["database", "schema", "table", "snapshot", "incremental"];
  const sinkKeys = ["database", "schema", "table", "write"];
  for (const table of value.source.tables) {
    if (
      !table?.table ||
      !hasOnlyKeys(table, sourceKeys) ||
      [table.database, table.schema, table.table].some((name) => name?.includes("."))
    )
      throw new Error("无效的来源表配置");
  }
  for (const table of value.sink.tables) {
    if (
      !table?.table ||
      !table.write?.mode ||
      !hasOnlyKeys(table, sinkKeys) ||
      !hasOnlyKeys(table.write, ["mode", "primaryKeys", "transaction"]) ||
      [table.database, table.schema, table.table].some((name) => name?.includes("."))
    )
      throw new Error("无效的目标表配置");
  }
  if (realtime) {
    if (
      value.source.syncMode !== undefined ||
      (value.source.startup !== undefined &&
        (!STARTUP.has(value.source.startup.mode) ||
          !hasOnlyKeys(value.source.startup, [
            "mode",
            "file",
            "pos",
            "gtidSet",
            "timestampMillis",
            "skipEvents",
            "skipRows",
          ])))
    )
      throw new Error("CDC 启动位置配置无效");
  } else if (
    value.source.startup !== undefined ||
    (value.source.syncMode !== undefined &&
      !["FULL", "INCREMENTAL", "FULL_AND_INCREMENTAL"].includes(value.source.syncMode))
  ) {
    throw new Error("JDBC 同步模式配置无效");
  }
  const steps = displaySyncSteps(value);
  const policy = value.sink.schemaPolicy;
  if (
    !policy ||
    !["VERIFY", "CREATE_OR_ALTER"].includes(policy.initial) ||
    !policy.evolution ||
    typeof policy.evolution.enabled !== "boolean" ||
    typeof policy.evolution.allowDropColumns !== "boolean" ||
    !hasOnlyKeys(policy, ["initial", "evolution"]) ||
    !hasOnlyKeys(policy.evolution, ["enabled", "allowDropColumns"]) ||
    (policy.evolution.allowDropColumns && !policy.evolution.enabled) ||
    (policy.evolution.enabled && (!realtime || !steps.includes("INCREMENTAL"))) ||
    (policy.initial === "CREATE_OR_ALTER" && !steps.includes("FULL"))
  )
    throw new Error("无效的目标 Schema 策略");
  const allSources = new Set(value.source.tables.map(path));
  const allSinks = new Set(value.sink.tables.map(path));
  const mappedSources = new Set<string>();
  const mappedSinks = new Set<string>();
  if (allSources.size !== value.source.tables.length || allSinks.size !== value.sink.tables.length)
    throw new Error("表配置不可重复");
  for (const route of value.route) {
    if (
      !route ||
      !hasOnlyKeys(route, ["source-table", "sink-table"]) ||
      !allSources.has(route["source-table"]) ||
      !allSinks.has(route["sink-table"]) ||
      mappedSources.has(route["source-table"]) ||
      mappedSinks.has(route["sink-table"])
    )
      throw new Error("无效或重复的表路由");
    mappedSources.add(route["source-table"]);
    mappedSinks.add(route["sink-table"]);
  }
  for (const source of value.source.tables) {
    if (
      source.snapshot &&
      (!realtime ||
        !steps.includes("FULL") ||
        !source.snapshot.chunkKeyColumn?.trim() ||
        !hasOnlyKeys(source.snapshot, ["chunkKeyColumn"]))
    )
      throw new Error("Snapshot 配置无效");
    if (offlineIncremental(pipelineSolutionType(value), steps)) {
      if (!validIncrementalConfig(source.incremental, !steps.includes("FULL")))
        throw new Error("离线增量配置不完整");
    } else if (source.incremental !== undefined) throw new Error("无效的增量配置");
  }
  for (const target of value.sink.tables) {
    const write = target.write;
    if (
      !WRITES.has(write.mode) ||
      (write.primaryKeys &&
        (!write.primaryKeys.length ||
          new Set(write.primaryKeys).size !== write.primaryKeys.length ||
          policy.evolution.enabled))
    )
      throw new Error("目标写入配置无效");
    const retract =
      realtime &&
      write.mode === "RETRACT" &&
      pipelineSolutionType(value) === "SINGLE_TABLE_REALTIME" &&
      write.transaction?.mode === "CHECKPOINT_XA" &&
      !write.primaryKeys &&
      !policy.evolution.enabled &&
      steps.includes("INCREMENTAL");
    if (
      (write.mode === "RETRACT" && !retract) ||
      (write.transaction !== undefined && !retract) ||
      ((realtime || (steps.includes("INCREMENTAL") && !realtime)) &&
        write.mode !== "UPSERT" &&
        !retract)
    )
      throw new Error("不支持的写入模式");
  }
  const expectedMode = realtime && steps.includes("INCREMENTAL") ? "STREAMING" : "BATCH";
  if (
    value.pipeline?.["execution.runtime-mode"] !== undefined &&
    value.pipeline["execution.runtime-mode"] !== expectedMode
  )
    throw new Error("Pipeline 运行模式与 Source 不一致");
}

export function readSyncParameters(schemaVersion: number, content: unknown): DataSyncParameters {
  return parseSyncParameters(schemaVersion, content);
}
