import type { DataSyncPipelineDefinition, DataSyncParameters } from "./types";

export type PipelineDefinition = DataSyncPipelineDefinition;
export type PipelineEndpoint = "source" | "sink";
export interface PipelineTable {
  database: string;
  table: string;
}

const TABLE_PATH = /^[A-Za-z0-9_$-]+\.[A-Za-z0-9_$-]+$/;
const SOURCE_PATTERN = /^\\Q([A-Za-z0-9_$-]+\.[A-Za-z0-9_$-]+)\\E$/;
const CREDENTIAL_KEYS = new Set([
  "password", "passwd", "token", "secret", "secretkey", "accesskey",
  "apikey", "privatekey", "credentials",
]);

function object(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function tablePath(value: PipelineTable): string {
  const path = value.database + "." + value.table;
  if (!TABLE_PATH.test(path)) throw new Error("表名必须是 database.table");
  return path;
}

export function sourceTable(definition: PipelineDefinition): PipelineTable | undefined {
  const match = SOURCE_PATTERN.exec(definition.source.tables);
  if (!match) return undefined;
  const [database, table] = match[1].split(".");
  return { database, table };
}

export function sinkTable(definition: PipelineDefinition): PipelineTable | undefined {
  const path = definition.route[0]?.["sink-table"];
  if (typeof path !== "string" || !TABLE_PATH.test(path)) return undefined;
  const [database, table] = path.split(".");
  return { database, table };
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
      "exclude.schema.changes": ["drop.column"],
    },
    route: [{ "source-table": "", "sink-table": "" }],
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
    next.route[0]["source-table"] = "";
    next.route[0]["sink-table"] = "";
  } else {
    next.sink["datasource-id"] = id;
    next.route[0]["sink-table"] = "";
  }
  return next;
}

export function setPipelineSourceTable(
  definition: PipelineDefinition,
  table: PipelineTable,
): PipelineDefinition {
  const next = structuredClone(definition);
  const literal = "\\Q" + tablePath(table) + "\\E";
  next.source.tables = literal;
  next.route[0]["source-table"] = literal;
  // A source change invalidates the previous target mapping.
  next.route[0]["sink-table"] = "";
  return next;
}

export function setPipelineSinkTable(
  definition: PipelineDefinition,
  table: PipelineTable,
): PipelineDefinition {
  const next = structuredClone(definition);
  next.route[0]["sink-table"] = tablePath(table);
  return next;
}

export function setPipelineOption(
  definition: PipelineDefinition,
  option: string,
  value: string | number | undefined,
): PipelineDefinition {
  if (!["parallelism", "local-time-zone"].includes(option)) {
    throw new Error("不支持修改此 Pipeline 参数：" + option);
  }
  const next = structuredClone(definition);
  if (value === undefined || value === "") delete next.pipeline[option];
  else next.pipeline[option] = value;
  return next;
}

export function setSchemaChangeBehavior(
  definition: PipelineDefinition,
  enabled: boolean,
): PipelineDefinition {
  const next = structuredClone(definition);
  next.pipeline["schema.change.behavior"] = enabled ? "evolve" : "exception";
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

/** Matches the enabled MySQL INITIAL -> JDBC single-table Runtime profile. */
export function validatePipelineDefinition(definition: unknown): asserts definition is PipelineDefinition {
  if (!object(definition) || !object(definition.source) ||
      !object(definition.sink) || !object(definition.pipeline) ||
      !Array.isArray(definition.route) || definition.route.length !== 1 ||
      !object(definition.route[0])) {
    throw new Error("无效的 Pipeline 配置结构");
  }
  rejectCredentials(definition);
  const source = definition.source;
  const sink = definition.sink;
  const route = definition.route[0];
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
  if (typeof source.tables !== "string" ||
      !SOURCE_PATTERN.test(source.tables) ||
      route["source-table"] !== source.tables ||
      typeof route["sink-table"] !== "string" ||
      !TABLE_PATH.test(route["sink-table"])) {
    throw new Error("请选择有效的单表来源和目标表映射");
  }
  if (!Array.isArray(sink["exclude.schema.changes"]) ||
      sink["exclude.schema.changes"].length !== 1 ||
      sink["exclude.schema.changes"][0] !== "drop.column") {
    throw new Error("当前 Runtime 必须禁止删除目标字段");
  }
  if (definition.transform !== undefined &&
      (!Array.isArray(definition.transform) || definition.transform.length !== 0)) {
    throw new Error("当前 Runtime 不支持 Transform");
  }
  const change = pipeline["schema.change.behavior"];
  if (change !== undefined && change !== "evolve" && change !== "exception") {
    throw new Error("不支持的 Schema 变更策略");
  }
  const parallelism = pipeline.parallelism;
  if (parallelism !== undefined &&
      (typeof parallelism !== "number" && typeof parallelism !== "string" ||
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
    name, remark, expectedRevision,
    ...(parametersChanged ? { parameters: pipelineParameters(definition) } : {}),
  };
}

export function publishTaskRequest(version: number, expectedRevision: number) {
  return { version, expectedRevision };
}
