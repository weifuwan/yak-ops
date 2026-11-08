import type {
  DataSyncPipelineDefinition,
  DataSyncParameters,
  DataSyncSchemaChangeBehavior,
  DataSyncSchemaChangeEvent,
  DataSyncScope,
  DataSyncStep,
  DataSyncType,
} from "./types";

export type PipelineDefinition = DataSyncPipelineDefinition;
export type PipelineEndpoint = "source" | "sink";
export type PipelineStartupMode = "initial" | "snapshot" | "latest-offset";

export interface PipelineTable {
  database: string;
  table: string;
}

export interface PipelineTableMapping {
  source: PipelineTable;
  target: PipelineTable;
}

export type PipelineTransformKey = "projection" | "filter" | "primary-keys";


const TABLE_PATH = /^[A-Za-z0-9_$-]+\.[A-Za-z0-9_$-]+$/;
const DATABASE_NAME = /^[A-Za-z0-9_$-]+$/;
const DATABASE_PATTERN = /^([A-Za-z0-9_$-]+)\.\\\.\*$/;
const DATABASE_SINK_PATTERN = /^([A-Za-z0-9_$-]+)\.<>$/;

export const SCHEMA_CHANGE_BEHAVIORS: readonly DataSyncSchemaChangeBehavior[] = [
  "ignore",
  "lenient",
  "try_evolve",
  "evolve",
  "exception",
];

export const SCHEMA_CHANGE_EVENTS: readonly DataSyncSchemaChangeEvent[] = [
  "create.table",
  "add.column",
  "alter.column.type",
  "rename.column",
  "drop.column",
  "truncate.table",
  "drop.table",
  "alter.table.comment",
];

const CREDENTIAL_KEYS = new Set([
  "password",
  "passwd",
  "token",
  "secret",
  "secretkey",
  "accesskey",
  "apikey",
  "privatekey",
  "credentials",
]);

function object(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function tablePath(value: PipelineTable): string {
  const path = value.database + "." + value.table;
  if (!TABLE_PATH.test(path)) throw new Error("表名必须是 database.table");
  return path;
}

function parseTablePath(value: unknown): PipelineTable | undefined {
  if (typeof value !== "string" || !TABLE_PATH.test(value)) return undefined;
  const [database, table] = value.split(".");
  return { database, table };
}

function databaseTablePattern(database: string): string {
  if (!DATABASE_NAME.test(database)) throw new Error("数据库名称无效");
  return database + ".\\.*";
}

function parseDatabasePattern(value: unknown): string | undefined {
  if (typeof value !== "string") return undefined;
  return DATABASE_PATTERN.exec(value)?.[1];
}

function parseDatabaseSinkPattern(value: unknown): string | undefined {
  if (typeof value !== "string") return undefined;
  return DATABASE_SINK_PATTERN.exec(value)?.[1];
}

export function pipelineDatabaseRoute(
  definition: PipelineDefinition,
): { sourceDatabase: string; targetDatabase: string } | undefined {
  if (sourcePaths(definition).length !== 1 || definition.route.length !== 1) return undefined;
  const sourcePattern = sourcePaths(definition)[0];
  const route = definition.route[0];
  const sourceDatabase = parseDatabasePattern(sourcePattern);
  const targetDatabase = parseDatabaseSinkPattern(route["sink-table"]);
  if (
    !sourceDatabase ||
    route["source-table"] !== sourcePattern ||
    route["replace-symbol"] !== "<>" ||
    !targetDatabase
  ) return undefined;
  return { sourceDatabase, targetDatabase };
}

function sourcePaths(definition: PipelineDefinition): string[] {
  return definition.source.tables
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
}

function startupModeFromSteps(steps: readonly DataSyncStep[]): PipelineStartupMode {
  const full = steps.includes("FULL");
  const incremental = steps.includes("INCREMENTAL");
  if (full && incremental) return "initial";
  if (full) return "snapshot";
  if (incremental) return "latest-offset";
  throw new Error("全量同步和增量同步至少选择一项");
}

export function pipelineSteps(definition: PipelineDefinition): DataSyncStep[] {
  const mode = definition.source["scan.startup.mode"];
  if (mode === "snapshot") return ["SCHEMA", "FULL"];
  if (mode === "latest-offset") return ["SCHEMA", "INCREMENTAL"];
  if (mode === "initial") return ["SCHEMA", "FULL", "INCREMENTAL"];
  throw new Error("不支持的 MySQL 启动模式");
}

export function pipelineSyncType(definition: PipelineDefinition): DataSyncType {
  return pipelineSteps(definition).includes("INCREMENTAL") ? "REALTIME" : "OFFLINE";
}

export function pipelineScope(definition: PipelineDefinition): DataSyncScope {
  const paths = sourcePaths(definition);
  if (paths.length === 1 && parseDatabasePattern(paths[0])) return "DATABASE";
  return paths.length === 1 ? "SINGLE_TABLE" : "DATABASE";
}

export function setPipelineSteps(
  definition: PipelineDefinition,
  steps: readonly DataSyncStep[],
): PipelineDefinition {
  const next = structuredClone(definition);
  const startupMode = startupModeFromSteps(steps);
  next.source["scan.startup.mode"] = startupMode;
  next.pipeline["execution.runtime-mode"] =
    startupMode === "snapshot" ? "BATCH" : "STREAMING";
  return next;
}

export function sourceTables(definition: PipelineDefinition): PipelineTable[] {
  return sourcePaths(definition)
    .map(parseTablePath)
    .filter((value): value is PipelineTable => Boolean(value));
}

export function pipelineTableMappings(
  definition: PipelineDefinition,
): PipelineTableMapping[] {
  return definition.route.flatMap((route) => {
    const source = parseTablePath(route["source-table"]);
    const target = parseTablePath(route["sink-table"]);
    return source && target ? [{ source, target }] : [];
  });
}

export function sourceTable(definition: PipelineDefinition): PipelineTable | undefined {
  return sourceTables(definition)[0];
}

export function sinkTable(definition: PipelineDefinition): PipelineTable | undefined {
  return pipelineTableMappings(definition)[0]?.target;
}

export function targetTableName(
  definition: PipelineDefinition,
  source: PipelineTable,
): string {
  const sourcePath = tablePath(source);
  const route = definition.route.find((item) => item["source-table"] === sourcePath);
  const target = parseTablePath(route?.["sink-table"]);
  return target?.table || "";
}

export function transformValue(
  definition: PipelineDefinition,
  source: PipelineTable,
  key: PipelineTransformKey,
): string {
  const sourcePath = tablePath(source);
  const transform = definition.transform?.find(
    (item) => item["source-table"] === sourcePath,
  );
  const value = transform?.[key];
  return typeof value === "string" ? value : "";
}

export function setPipelineTransformValue(
  definition: PipelineDefinition,
  source: PipelineTable,
  key: PipelineTransformKey,
  value: string,
): PipelineDefinition {
  const next = structuredClone(definition);
  const sourcePath = tablePath(source);
  const transforms = next.transform || [];
  let transform = transforms.find((item) => item["source-table"] === sourcePath);
  const hasValue = Boolean(value.trim());

  if (hasValue) {
    if (!transform) {
      transform = { "source-table": sourcePath };
      transforms.push(transform);
    }
    transform[key] = value;
  } else if (transform) {
    delete transform[key];
  }

  next.transform = transforms.filter((item) =>
    Object.keys(item).some((itemKey) => itemKey !== "source-table"),
  );
  if (!next.transform.length) delete next.transform;
  return next;
}

function chunkKeyColumns(definition: PipelineDefinition): Map<string, string> {
  const value = definition.source["scan.incremental.snapshot.chunk.key-column"];
  const result = new Map<string, string>();
  if (typeof value !== "string") return result;

  for (const item of value.split(";")) {
    const entry = item.trim();
    if (!entry) continue;
    const separator = entry.lastIndexOf(":");
    if (separator <= 0 || separator === entry.length - 1) continue;
    result.set(entry.slice(0, separator), entry.slice(separator + 1));
  }
  return result;
}

export function chunkKeyColumn(
  definition: PipelineDefinition,
  source: PipelineTable,
): string {
  return chunkKeyColumns(definition).get(tablePath(source)) || "";
}

export function setPipelineChunkKeyColumn(
  definition: PipelineDefinition,
  source: PipelineTable,
  column: string,
): PipelineDefinition {
  const next = structuredClone(definition);
  const values = chunkKeyColumns(next);
  const sourcePath = tablePath(source);
  const nextColumn = column.trim();

  if (nextColumn) values.set(sourcePath, nextColumn);
  else values.delete(sourcePath);

  if (values.size) {
    next.source["scan.incremental.snapshot.chunk.key-column"] =
      Array.from(values, ([table, key]) => table + ":" + key).join(";");
  } else {
    delete next.source["scan.incremental.snapshot.chunk.key-column"];
  }
  return next;
}

export function createPipelineDefinition(
  steps: readonly DataSyncStep[] = ["SCHEMA", "FULL", "INCREMENTAL"],
): PipelineDefinition {
  const startupMode = startupModeFromSteps(steps);
  return {
    source: {
      type: "mysql",
      "datasource-id": "",
      tables: "",
      "scan.startup.mode": startupMode,
    },
    sink: {
      type: "jdbc",
      "datasource-id": "",
      "exclude.schema.changes": ["drop.column", "truncate.table", "drop.table"],
    },
    route: [],
    pipeline: {
      "execution.runtime-mode": startupMode === "snapshot" ? "BATCH" : "STREAMING",
      "schema.change.behavior": "evolve",
      parallelism: 1,
    },
  };
}

/** Read the exact persisted Pipeline tree, never rebuild it from display fields. */
export function readPipelineDefinition(parameters: unknown): PipelineDefinition {
  if (!object(parameters) || !object(parameters.definition)) {
    throw new Error("缺少 Flink CDC Pipeline definition");
  }
  const definition = structuredClone(parameters.definition) as unknown as PipelineDefinition;
  validatePipelineDefinition(definition);
  return definition;
}

export function pipelineParameters(definition: PipelineDefinition): DataSyncParameters {
  validatePipelineDefinition(definition);
  return { definition: structuredClone(definition) };
}

export function setPipelineDatabaseScope(
  definition: PipelineDefinition,
  sourceDatabase: string,
  targetDatabase = "",
): PipelineDefinition {
  const next = structuredClone(definition);
  const sourcePattern = databaseTablePattern(sourceDatabase);
  next.source.tables = sourcePattern;
  delete next.source["scan.incremental.snapshot.chunk.key-column"];
  next.route = [{
    "source-table": sourcePattern,
    "sink-table": targetDatabase ? targetDatabase + ".<>" : "",
    "replace-symbol": "<>",
  }];
  delete next.transform;
  return next;
}

export function setPipelineDatasource(
  definition: PipelineDefinition,
  side: PipelineEndpoint,
  id: string,
): PipelineDefinition {
  const next = structuredClone(definition);
  if (side === "source") {
    next.source["datasource-id"] = id;
    next.source.tables = "";
    delete next.source["scan.incremental.snapshot.chunk.key-column"];
    next.route = [];
    delete next.transform;
  } else {
    next.sink["datasource-id"] = id;
    next.route = next.route.map((route) => ({ ...route, "sink-table": "" }));
  }
  return next;
}

export function setPipelineSourceTable(
  definition: PipelineDefinition,
  table: PipelineTable,
): PipelineDefinition {
  const next = structuredClone(definition);
  const path = tablePath(table);
  next.source.tables = path;
  delete next.source["scan.incremental.snapshot.chunk.key-column"];
  next.route = [{ "source-table": path, "sink-table": "" }];
  delete next.transform;
  return next;
}

export function addPipelineSourceTables(
  definition: PipelineDefinition,
  tables: PipelineTable[],
  targetDatabase = "",
): PipelineDefinition {
  const next = structuredClone(definition);
  const existingRoutes = new Map(
    next.route.map((route) => [String(route["source-table"] || ""), route]),
  );
  const paths = Array.from(new Set([
    ...sourcePaths(next),
    ...tables.map(tablePath),
  ]));

  next.source.tables = paths.join(",");
  next.route = paths.map((path) => {
    const existing = existingRoutes.get(path);
    if (existing) return existing;
    const source = parseTablePath(path)!;
    return {
      "source-table": path,
      "sink-table": targetDatabase ? targetDatabase + "." + source.table : "",
    };
  });
  return next;
}

export function removePipelineSourceTable(
  definition: PipelineDefinition,
  source: PipelineTable,
): PipelineDefinition {
  const next = structuredClone(definition);
  const path = tablePath(source);
  next.route = next.route.filter((route) => route["source-table"] !== path);
  next.source.tables = next.route
    .map((route) => String(route["source-table"] || ""))
    .filter(Boolean)
    .join(",");

  if (next.transform) {
    next.transform = next.transform.filter((item) => item["source-table"] !== path);
    if (!next.transform.length) delete next.transform;
  }

  const chunkKeys = chunkKeyColumns(next);
  chunkKeys.delete(path);
  if (chunkKeys.size) {
    next.source["scan.incremental.snapshot.chunk.key-column"] =
      Array.from(chunkKeys, ([table, key]) => table + ":" + key).join(";");
  } else {
    delete next.source["scan.incremental.snapshot.chunk.key-column"];
  }
  return next;
}

export function setPipelineSinkDatabase(
  definition: PipelineDefinition,
  database: string,
): PipelineDefinition {
  const next = structuredClone(definition);
  next.route = next.route.map((route) => {
    if (parseDatabasePattern(route["source-table"])) {
      return {
        ...route,
        "sink-table": database ? database + ".<>" : "",
        "replace-symbol": "<>",
      };
    }
    const source = parseTablePath(route["source-table"]);
    const currentTarget = parseTablePath(route["sink-table"]);
    const table = currentTarget?.table || source?.table || "";
    return {
      ...route,
      "sink-table": database && table ? database + "." + table : "",
    };
  });
  return next;
}

export function setPipelineTargetTableName(
  definition: PipelineDefinition,
  source: PipelineTable,
  database: string,
  table: string,
): PipelineDefinition {
  const next = structuredClone(definition);
  const sourcePath = tablePath(source);
  const route = next.route.find((item) => item["source-table"] === sourcePath);
  if (!route) throw new Error("来源表映射不存在");
  const targetTable = table.trim();
  route["sink-table"] = database && targetTable
    ? database + "." + targetTable
    : "";
  return next;
}

export function setPipelineSinkTable(
  definition: PipelineDefinition,
  table: PipelineTable,
): PipelineDefinition {
  const next = structuredClone(definition);
  if (!next.route.length) throw new Error("请先选择来源表");
  next.route[0]["sink-table"] = tablePath(table);
  return next;
}

function rejectCredentials(value: unknown): void {
  if (Array.isArray(value)) {
    value.forEach(rejectCredentials);
    return;
  }
  if (!object(value)) return;
  for (const [key, child] of Object.entries(value)) {
    if (CREDENTIAL_KEYS.has(key.replace(/[_-]/g, "").toLowerCase())) {
      throw new Error("请引用数据源，不允许在 Pipeline 中保存密码或凭据");
    }
    rejectCredentials(child);
  }
}

/** Validates the enabled MySQL -> JDBC Pipeline profiles. */
export function validatePipelineDefinition(definition: unknown): asserts definition is PipelineDefinition {
  if (!object(definition) || !object(definition.source) ||
      !object(definition.sink) || !object(definition.pipeline) ||
      !Array.isArray(definition.route)) {
    throw new Error("无效的 Pipeline 配置结构");
  }

  rejectCredentials(definition);

  const source = definition.source;
  const sink = definition.sink;
  const pipeline = definition.pipeline;

  const startupMode = source["scan.startup.mode"];
  const runtimeMode = pipeline["execution.runtime-mode"];
  const supportedStartupMode =
    startupMode === "initial" || startupMode === "snapshot" || startupMode === "latest-offset";
  const supportedRuntimeMode =
    (startupMode === "snapshot" && runtimeMode === "BATCH") ||
    ((startupMode === "initial" || startupMode === "latest-offset") &&
      runtimeMode === "STREAMING");
  if (source.type !== "mysql" || sink.type !== "jdbc" ||
      !supportedStartupMode || !supportedRuntimeMode) {
    throw new Error("当前仅支持 MySQL initial/snapshot/latest-offset → JDBC");
  }

  for (const endpoint of [source, sink]) {
    const id = endpoint["datasource-id"];
    if (typeof id !== "string" || !/^[1-9]\d*$/.test(id) ||
        !Number.isSafeInteger(Number(id)) || Number(id) > 2147483647) {
      throw new Error("请选择有效的数据源");
    }
  }

  if (typeof source.tables !== "string") {
    throw new Error("请选择有效的来源表");
  }
  const tables = source.tables.split(",").map((item) => item.trim()).filter(Boolean);
  const sourceSet = new Set(tables);
  const databasePattern =
    tables.length === 1 ? parseDatabasePattern(tables[0]) : undefined;
  if (databasePattern) {
    if (definition.route.length !== 1) {
      throw new Error("整库同步必须配置一条整库路由");
    }
    const route = definition.route[0];
    if (
      !object(route) ||
      route["source-table"] !== tables[0] ||
      route["replace-symbol"] !== "<>" ||
      !parseDatabaseSinkPattern(route["sink-table"])
    ) {
      throw new Error("整库同步路由必须保持源表名称映射");
    }
    if (
      definition.transform !== undefined &&
      (!Array.isArray(definition.transform) || definition.transform.length > 0)
    ) {
      throw new Error("整库同步暂不支持表级 Transform");
    }
    if (source["scan.incremental.snapshot.chunk.key-column"] !== undefined) {
      throw new Error("整库同步暂不支持表级快照切分列");
    }
  } else {
    if (!tables.length || tables.some((item) => !TABLE_PATH.test(item)) ||
        new Set(tables).size !== tables.length) {
      throw new Error("请选择有效且不重复的来源表");
    }

    if (definition.route.length !== tables.length) {
      throw new Error("每张来源表都必须配置一条目标表映射");
    }

    const routedSources = new Set<string>();
    const routedTargets = new Set<string>();
    for (const route of definition.route) {
      if (!object(route)) throw new Error("无效的目标表映射");

      const routeSource = route["source-table"];
      const routeTarget = route["sink-table"];
      if (typeof routeSource !== "string" || !TABLE_PATH.test(routeSource) ||
          !sourceSet.has(routeSource) || routedSources.has(routeSource)) {
        throw new Error("来源表映射必须与已选来源表一一对应");
      }
      if (typeof routeTarget !== "string" || !TABLE_PATH.test(routeTarget)) {
        throw new Error("请配置有效的目标表映射");
      }
      if (routedTargets.has(routeTarget)) {
        throw new Error("不同来源表不能映射到同一目标表");
      }
      routedSources.add(routeSource);
      routedTargets.add(routeTarget);
    }

    if (routedSources.size !== sourceSet.size) {
      throw new Error("每张来源表都必须配置一条目标表映射");
    }
  }

  const excludedSchemaChanges = sink["exclude.schema.changes"];
  if (excludedSchemaChanges !== undefined &&
      (!Array.isArray(excludedSchemaChanges) ||
       excludedSchemaChanges.some((event) =>
         typeof event !== "string" ||
         !SCHEMA_CHANGE_EVENTS.includes(event as DataSyncSchemaChangeEvent)))) {
    throw new Error("包含不支持的 Schema 变更类型");
  }

  if (definition.transform !== undefined) {
    if (!Array.isArray(definition.transform)) {
      throw new Error("Transform 配置无效");
    }
    const transformedSources = new Set<string>();
    for (const transform of definition.transform) {
      if (!object(transform)) throw new Error("Transform 配置无效");
      const transformSource = transform["source-table"];
      if (typeof transformSource !== "string" ||
          !sourceSet.has(transformSource) ||
          transformedSources.has(transformSource)) {
        throw new Error("Transform 必须与来源表一一对应");
      }
      transformedSources.add(transformSource);

      for (const key of Object.keys(transform)) {
        if (!["source-table", "projection", "filter", "primary-keys"].includes(key)) {
          throw new Error("包含当前页面不支持的 Transform 配置");
        }
        if (key !== "source-table" &&
            (typeof transform[key] !== "string" || !String(transform[key]).trim())) {
          throw new Error("Transform 参数不能为空");
        }
      }
    }
  }

  const chunkKey = source["scan.incremental.snapshot.chunk.key-column"];
  if (chunkKey !== undefined) {
    if (typeof chunkKey !== "string" || !chunkKey.trim()) {
      throw new Error("快照切分列配置无效");
    }
    const configuredTables = new Set<string>();
    for (const item of chunkKey.split(";").map((entry) => entry.trim()).filter(Boolean)) {
      const separator = item.lastIndexOf(":");
      const sourcePath = separator > 0 ? item.slice(0, separator) : "";
      const column = separator > 0 ? item.slice(separator + 1) : "";
      if (!sourceSet.has(sourcePath) || !column || configuredTables.has(sourcePath)) {
        throw new Error("快照切分列必须与已选来源表对应");
      }
      configuredTables.add(sourcePath);
    }
  }

  const change = pipeline["schema.change.behavior"];
  if (change !== undefined &&
      (typeof change !== "string" ||
       !SCHEMA_CHANGE_BEHAVIORS.includes(change as DataSyncSchemaChangeBehavior))) {
    throw new Error("不支持的 Schema 变更策略");
  }

  const parallelism = pipeline.parallelism;
  if (parallelism !== undefined &&
      ((typeof parallelism !== "number" && typeof parallelism !== "string") ||
       !/^\d+$/.test(String(parallelism)) ||
       Number(parallelism) < 1 || Number(parallelism) > 128)) {
    throw new Error("并行度必须在 1 到 128 之间");
  }

  if (pipeline["local-time-zone"] !== undefined &&
      typeof pipeline["local-time-zone"] !== "string") {
    throw new Error("运行时区必须是字符串");
  }
}

/** API payloads are built directly from the same persisted Pipeline tree. */
export function createTaskRequest(name: string, remark: string, definition: PipelineDefinition) {
  return { name, remark, parameters: pipelineParameters(definition) };
}

export function updateTaskRequest(
  name: string,
  remark: string,
  definition: PipelineDefinition,
  expectedRevision: number,
  parametersChanged: boolean,
) {
  return {
    name,
    remark,
    expectedRevision,
    ...(parametersChanged ? { parameters: pipelineParameters(definition) } : {}),
  };
}

export function publishTaskRequest(version: number, expectedRevision: number) {
  return { version, expectedRevision };
}
