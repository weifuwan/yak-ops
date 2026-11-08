import type {
  DataSyncPipelineDefinition,
  DataSyncParameters,
  DataSyncSchemaChangeBehavior,
  DataSyncSchemaChangeEvent,
} from "./types";

export type PipelineDefinition = DataSyncPipelineDefinition;
export type PipelineEndpoint = "source" | "sink";

export interface PipelineTable {
  database: string;
  table: string;
}

export interface PipelineTableMapping {
  source: PipelineTable;
  target: PipelineTable;
}

const TABLE_PATH = /^[A-Za-z0-9_$-]+\.[A-Za-z0-9_$-]+$/;

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

function sourcePaths(definition: PipelineDefinition): string[] {
  return definition.source.tables
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
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

export function createPipelineDefinition(): PipelineDefinition {
  return {
    source: {
      type: "mysql",
      "datasource-id": "",
      tables: "",
      "scan.startup.mode": "initial",
    },
    sink: {
      type: "jdbc",
      "datasource-id": "",
      "exclude.schema.changes": ["drop.column", "truncate.table", "drop.table"],
    },
    route: [],
    pipeline: {
      "execution.runtime-mode": "STREAMING",
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

export function setPipelineDatasource(
  definition: PipelineDefinition,
  side: PipelineEndpoint,
  id: string,
): PipelineDefinition {
  const next = structuredClone(definition);
  if (side === "source") {
    next.source["datasource-id"] = id;
    next.source.tables = "";
    next.route = [];
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
  next.route = [{ "source-table": path, "sink-table": "" }];
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
  return next;
}

export function setPipelineSinkDatabase(
  definition: PipelineDefinition,
  database: string,
): PipelineDefinition {
  const next = structuredClone(definition);
  next.route = next.route.map((route) => {
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

/** Matches the enabled MySQL INITIAL -> JDBC multi-table Runtime profile. */
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

  if (source.type !== "mysql" || sink.type !== "jdbc" ||
      source["scan.startup.mode"] !== "initial" ||
      pipeline["execution.runtime-mode"] !== "STREAMING") {
    throw new Error("当前仅支持 MySQL INITIAL → JDBC STREAMING");
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
  if (!tables.length || tables.some((item) => !TABLE_PATH.test(item)) ||
      new Set(tables).size !== tables.length) {
    throw new Error("请选择有效且不重复的来源表");
  }

  if (definition.route.length !== tables.length) {
    throw new Error("每张来源表都必须配置一条目标表映射");
  }

  const sourceSet = new Set(tables);
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

  const excludedSchemaChanges = sink["exclude.schema.changes"];
  if (excludedSchemaChanges !== undefined &&
      (!Array.isArray(excludedSchemaChanges) ||
       excludedSchemaChanges.some((event) =>
         typeof event !== "string" ||
         !SCHEMA_CHANGE_EVENTS.includes(event as DataSyncSchemaChangeEvent)))) {
    throw new Error("包含不支持的 Schema 变更类型");
  }

  if (definition.transform !== undefined &&
      (!Array.isArray(definition.transform) || definition.transform.length !== 0)) {
    throw new Error("当前 Runtime 不支持 Transform");
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
