import type {
  DataSyncParameters,
  DataSyncIncrementalConfig,
  DataSyncProductType,
  DataSyncStep,
  DataSyncTableMapping,
  DataSyncTablePath,
} from "./types";
import {
  templateTarget,
  validateBatchSize,
  validateSourceOptions,
  validateSqlHooks,
  validateTableDml,
  validateTableRead,
  validateTableSnapshot,
  validateTypeMappings,
} from "./table-configuration.ts";

const WRITES = new Set(["APPEND", "UPSERT", "REPLACE", "RETRACT"]);
const STARTUP = new Set([
  "INITIAL",
  "SNAPSHOT",
  "EARLIEST_OFFSET",
  "LATEST_OFFSET",
  "SPECIFIC_OFFSETS",
  "TIMESTAMP",
]);
const PIPELINE_STRING_OPTIONS = [
  "name",
  "local-time-zone",
  "schema.change.behavior",
  "schema.operator.uid",
  "route-mode",
  "transform.decimal.precision.mode",
] as const;
const hasOnlyKeys = (value: object, keys: string[]) =>
  Object.keys(value).every((key) => keys.includes(key));
const path = (table: DataSyncTablePath) =>
  [table.database, table.schema, table.table].filter(Boolean).join(".");

/** Physical execution mode drives scheduling and instance actions. */
export const pipelineSyncType = (value: DataSyncParameters) =>
  value.pipeline["execution.runtime-mode"] === "STREAMING" ? "REALTIME" : "OFFLINE";

export const offlineIncremental = (product: DataSyncProductType, steps: DataSyncStep[]) =>
  product === "SINGLE_TABLE_BATCH" && steps.includes("INCREMENTAL");

/** Streaming definitions use INITIAL when no CDC startup position is supplied. */
export const usesCdcStartup = (value: DataSyncParameters) =>
  value.source.startup !== undefined || value.pipeline["execution.runtime-mode"] === "STREAMING";

/** Execution scope is independent of the replication product and selected strategy. */
export const pipelineTableScope = (value: DataSyncParameters) =>
  value.pipeline["pipeline.table-scope"] ??
  (value.source.tables.length > 1 ? "DATABASE" : "SINGLE");

/** Derive the application workflow from validated JSON, never from connector names. */
export function pipelineProductType(value: DataSyncParameters): DataSyncProductType {
  if (usesCdcStartup(value)) return "DATABASE_REPLICATION";
  if (pipelineTableScope(value) === "SINGLE") return "SINGLE_TABLE_BATCH";
  throw new Error("多表 JDBC 定义不属于当前同步产品，请使用整库实时或分别创建单表离线任务");
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

/** Parse the persisted Task JSON without converting YAML in the frontend. */
export function parseSyncParameters(schemaVersion: number, content: unknown): DataSyncParameters {
  if (schemaVersion !== 7 && schemaVersion !== 8) throw new Error("不支持的同步任务参数版本");
  if (!content || typeof content !== "object" || Array.isArray(content))
    throw new Error("无效的 JSON Pipeline 定义");
  const value = { ...(content as DataSyncParameters & { index?: unknown }) };
  delete value.index;
  if (value.transform === undefined) value.transform = [];
  if (!hasOnlyKeys(value, ["source", "sink", "route", "transform", "pipeline"]))
    throw new Error("Pipeline 包含未识别的配置项");
  validateSyncParameters(value, schemaVersion);
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
        ...(table.read ? { read: { ...table.read } } : {}),
        ...(table.options ? { options: { ...table.options } } : {}),
        ...(table.snapshot ? { snapshot: { ...table.snapshot } } : {}),
        ...(table.dml ? { dml: { events: [...table.dml.events] } } : {}),
      },
      target: {
        database: target.database,
        schema: target.schema,
        table: target.table,
        ...(target.write.primaryKeys ? { primaryKeys: target.write.primaryKeys } : {}),
      },
      writeMode: target.write.mode,
      ...(target.write.preSql ? { preSql: [...target.write.preSql] } : {}),
      ...(target.write.postSql ? { postSql: [...target.write.postSql] } : {}),
      ...(target.write.batchSize !== undefined ? { batchSize: target.write.batchSize } : {}),
      ...(table.incremental ? { incremental: table.incremental } : {}),
    };
  });
}

export function displaySyncSteps(value: DataSyncParameters): DataSyncStep[] {
  const dataSteps: DataSyncStep[] = usesCdcStartup(value)
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

export function validateSyncParameters(value: DataSyncParameters, schemaVersion = 8): void {
  if (
    !value?.source ||
    !value.sink ||
    !hasOnlyKeys(value.source, [
      "type",
      "datasourceId",
      "syncMode",
      "startup",
      "tables",
      ...(schemaVersion === 8 ? ["selection"] : []),
    ]) ||
    !hasOnlyKeys(value.sink, [
      "type",
      "datasourceId",
      "schemaPolicy",
      "tables",
      ...(schemaVersion === 8 ? ["mapping", "typeMappings"] : []),
    ]) ||
    typeof value.source.type !== "string" ||
    typeof value.sink.type !== "string" ||
    !/^[a-z][a-z0-9-]*$/.test(value.source.type) ||
    !/^[a-z][a-z0-9-]*$/.test(value.sink.type) ||
    !value.source.datasourceId ||
    !value.sink.datasourceId ||
    !Array.isArray(value.source.tables) ||
    !Array.isArray(value.sink.tables) ||
    !Array.isArray(value.route) ||
    !Array.isArray(value.transform) ||
    !value.source.tables.length ||
    value.source.tables.length > 100 ||
    value.source.tables.length !== value.sink.tables.length ||
    value.route.length !== value.source.tables.length ||
    !value.pipeline ||
    !hasOnlyKeys(value.pipeline, [
      "execution.runtime-mode",
      "pipeline.table-scope",
      "parallelism",
      ...PIPELINE_STRING_OPTIONS,
    ]) ||
    (value.pipeline["pipeline.table-scope"] !== undefined &&
      !["SINGLE", "DATABASE"].includes(value.pipeline["pipeline.table-scope"])) ||
    (value.pipeline["pipeline.table-scope"] === "SINGLE" && value.source.tables.length !== 1) ||
    !["BATCH", "STREAMING"].includes(value.pipeline["execution.runtime-mode"])
  ) {
    throw new Error("不支持的 Pipeline 定义");
  }
  const parallelism = value.pipeline.parallelism;
  if (
    PIPELINE_STRING_OPTIONS.some(
      (key) => value.pipeline[key] !== undefined && typeof value.pipeline[key] !== "string",
    ) ||
    (parallelism !== undefined &&
      (!/^\d+$/.test(String(parallelism)) ||
        !Number.isSafeInteger(Number(parallelism)) ||
        Number(parallelism) < 1))
  ) {
    throw new Error("无效的 Pipeline 配置项");
  }
  const realtime = usesCdcStartup(value);
  const sourceKeys = [
    "database",
    "schema",
    "table",
    "snapshot",
    "incremental",
    ...(schemaVersion === 8 ? ["read", "dml", "options"] : []),
  ];
  const sinkKeys = ["database", "schema", "table", "write"];
  for (const tables of [value.source.tables, value.sink.tables]) {
    const hasDatabase = Boolean(tables[0]?.database?.trim());
    if (tables.some((table) => Boolean(table?.database?.trim()) !== hasDatabase))
      throw new Error("同一端的表必须使用一致的数据库命名方式");
  }
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
      !hasOnlyKeys(table.write, [
        "mode",
        "primaryKeys",
        "transaction",
        ...(schemaVersion === 8 ? ["preSql", "postSql", "batchSize"] : []),
      ]) ||
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
  const schemaBehavior = value.pipeline["schema.change.behavior"]?.toLowerCase();
  if (schemaBehavior !== undefined) {
    if (!["evolve", "exception"].includes(schemaBehavior))
      throw new Error("不支持的 Schema 变更行为");
    if ((schemaBehavior === "evolve") !== policy.evolution.enabled)
      throw new Error("Pipeline 与目标 Schema 变更策略不一致");
  }
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
        (schemaVersion === 7 &&
          (!source.snapshot.chunkKeyColumn?.trim() ||
            !hasOnlyKeys(source.snapshot, ["chunkKeyColumn"]))))
    )
      throw new Error("Snapshot 配置无效");
    validateTableSnapshot(source.snapshot);
    validateTableRead(source.read);
    validateSourceOptions(source.options);
    validateTableDml(source.dml);
    if (source.read !== undefined && realtime) throw new Error("读取过滤和切分键仅用于单表离线");
    if (source.options !== undefined && realtime) throw new Error("读取引擎参数仅用于单表离线");
    if (source.dml !== undefined && !realtime) throw new Error("DML 事件配置仅用于实时同步");
    if (!realtime && steps.includes("INCREMENTAL")) {
      if (!validIncrementalConfig(source.incremental, !steps.includes("FULL")))
        throw new Error("离线增量配置不完整");
    } else if (source.incremental !== undefined) throw new Error("无效的增量配置");
  }
  for (const target of value.sink.tables) {
    const write = target.write;
    if (
      write.mode === "REPLACE" &&
      (schemaVersion !== 8 || realtime || value.sink.type !== "jdbc-mysql")
    )
      throw new Error("替换写入仅支持 MySQL 目标的单表离线同步");
    validateSqlHooks(write);
    validateBatchSize(write.batchSize);
    if (realtime && (write.preSql !== undefined || write.postSql !== undefined))
      throw new Error("准备语句和完成语句仅用于离线同步");
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
      pipelineTableScope(value) === "SINGLE" &&
      write.transaction?.mode === "CHECKPOINT_XA" &&
      !write.primaryKeys &&
      !policy.evolution.enabled &&
      steps.includes("INCREMENTAL");
    if (
      (write.mode === "RETRACT" && !retract) ||
      (write.transaction !== undefined && !retract) ||
      (steps.includes("INCREMENTAL") && write.mode !== "UPSERT" && !retract) ||
      (retract && write.batchSize !== undefined)
    )
      throw new Error("不支持的写入模式");
  }
  const selection = value.source.selection;
  const mapping = value.sink.mapping;
  if (selection !== undefined || mapping !== undefined) {
    if (
      !realtime ||
      !selection ||
      !mapping ||
      selection.mode !== "REGEX" ||
      !hasOnlyKeys(selection, ["mode", "databasePattern", "tablePattern", "snapshot", "dml"]) ||
      typeof selection.databasePattern !== "string" ||
      !selection.databasePattern.trim() ||
      typeof selection.tablePattern !== "string" ||
      !selection.tablePattern.trim() ||
      !hasOnlyKeys(mapping, ["database", "schema", "table", "write"]) ||
      !mapping.write ||
      !hasOnlyKeys(mapping.write, ["mode", "primaryKeys", "transaction", "batchSize"]) ||
      !WRITES.has(mapping.write.mode) ||
      mapping.write.mode !== "UPSERT" ||
      mapping.write.transaction !== undefined ||
      mapping.write.primaryKeys !== undefined ||
      (mapping.database !== undefined && typeof mapping.database !== "string") ||
      (mapping.schema !== undefined && typeof mapping.schema !== "string") ||
      typeof mapping.table !== "string"
    )
      throw new Error("正则选表或目标映射规则无效");
    if (pipelineTableScope(value) !== "DATABASE") throw new Error("正则选表需要整库范围");
    if (
      value.source.startup?.mode !== "INITIAL" ||
      !steps.includes("INCREMENTAL") ||
      !steps.includes("FULL") ||
      policy.initial !== "CREATE_OR_ALTER" ||
      !policy.evolution.enabled
    )
      throw new Error("正则选表需要从全量开始持续同步并创建和同步表结构");
    validateTableSnapshot(selection.snapshot);
    validateTableDml(selection.dml);
    validateBatchSize(mapping.write.batchSize);
    if (selection.snapshot && !steps.includes("FULL"))
      throw new Error("当前读取策略不支持全量配置");
    for (const source of value.source.tables) templateTarget(mapping, source);
  }
  validateTypeMappings(value.sink.typeMappings);
  for (const transform of value.transform) {
    if (
      !transform ||
      typeof transform["source-table"] !== "string" ||
      !transform["source-table"].trim() ||
      !hasOnlyKeys(transform, [
        "source-table",
        "projection",
        "filter",
        "primary-keys",
        "partition-keys",
        "table-options",
        "table-options.delimiter",
        "description",
        "converter-after-transform",
      ]) ||
      Object.values(transform).some((field) => typeof field !== "string")
    ) {
      throw new Error("无效的转换规则");
    }
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
