import { parseSyncParameters } from "./parameters";
import { buildSyncParameters, toSyncTaskRecord } from "./definition";
import type {
  DataSyncParameters,
  DataSyncTaskRecord,
  DataSyncTaskSavePayload,
  DataSyncWriteMode,
  TaskParameterVersion,
} from "./types";

/** Object key ordering has no meaning; arrays retain authored order. */
export function parameterFingerprint(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(parameterFingerprint).join(",")}]`;
  if (value && typeof value === "object")
    return `{${Object.entries(value)
      .filter(([, item]) => item !== undefined)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, item]) => `${JSON.stringify(key)}:${parameterFingerprint(item)}`)
      .join(",")}}`;
  return JSON.stringify(value) ?? "";
}

export function matchesSavedTask(
  request: DataSyncTaskSavePayload,
  saved: DataSyncTaskRecord,
): boolean {
  return (
    request.name === saved.name &&
    (request.remark || "") === (saved.remark || "") &&
    request.debug === saved.debug &&
    parameterFingerprint(buildSyncParameters(request)) ===
      parameterFingerprint(
        buildSyncParameters({ ...saved, writeMode: saved.writeMode as DataSyncWriteMode }),
      )
  );
}

/** Restoring parameters retains the task's current metadata and concurrency revision. */
export function restoreParameterVersion(
  task: DataSyncTaskRecord,
  version: TaskParameterVersion,
): DataSyncTaskRecord {
  if (task.id !== version.definitionId) throw new Error("参数版本不属于当前任务");
  return toSyncTaskRecord({
    ...task,
    taskType: "DATA_SYNC",
    parameterSchemaVersion: version.parameterSchemaVersion,
    parameters: parseSyncParameters(version.parameterSchemaVersion, version.parameters),
  });
}

export interface ParameterChange {
  field: string;
  before: string;
  after: string;
}

const path = (table: { database?: string; schema?: string; table: string }) =>
  [table.database, table.schema, table.table].filter(Boolean).join(".");
const LABELS: Record<string, string> = {
  type: "连接器",
  datasourceId: "数据源",
  syncMode: "读取策略",
  startup: "启动位置",
  mode: "模式",
  file: "日志文件",
  pos: "日志位置",
  gtidSet: "GTID",
  timestampMillis: "启动时间戳",
  skipEvents: "跳过事件",
  skipRows: "跳过行",
  initial: "初始表结构",
  evolution: "结构变更",
  enabled: "启用",
  allowDropColumns: "允许删除字段",
  schemaPolicy: "DDL 策略",
  parallelism: "并行度",
  "local-time-zone": "时区",
  "execution.runtime-mode": "运行模式",
  "pipeline.table-scope": "表范围",
  "schema.change.behavior": "结构变更行为",
};

function flatten(value: unknown, prefix: string, result: Map<string, unknown>) {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    for (const [key, item] of Object.entries(value))
      if (item !== undefined) flatten(item, `${prefix} / ${LABELS[key] || key}`, result);
  } else result.set(prefix, value);
}

function parameterFields(parameters: DataSyncParameters) {
  const result = new Map<string, unknown>();
  const { tables: sourceTables, ...source } = parameters.source;
  const { tables: sinkTables, ...sink } = parameters.sink;
  flatten(source, "读端", result);
  flatten(sink, "写端", result);
  for (const table of sourceTables) result.set(`来源表 / ${path(table)}`, table);
  for (const table of sinkTables) result.set(`目标表 / ${path(table)}`, table);
  for (const route of parameters.route)
    result.set(`表映射 / ${route["source-table"]}`, route["sink-table"]);
  parameters.transform.forEach((rule, index) => result.set(`转换规则 / ${index + 1}`, rule));
  flatten(parameters.pipeline, "引擎", result);
  return result;
}

/** Match tables and routes by identity so unrelated row order does not create false differences. */
export function compareParameterVersions(
  before: TaskParameterVersion,
  after: TaskParameterVersion,
): ParameterChange[] {
  const left = parameterFields(
    parseSyncParameters(before.parameterSchemaVersion, before.parameters),
  );
  const right = parameterFields(
    parseSyncParameters(after.parameterSchemaVersion, after.parameters),
  );
  const display = (value: unknown) =>
    value === undefined ? "—" : typeof value === "string" ? value : JSON.stringify(value, null, 2);
  return [...new Set([...left.keys(), ...right.keys()])]
    .sort()
    .filter(
      (field) => parameterFingerprint(left.get(field)) !== parameterFingerprint(right.get(field)),
    )
    .map((field) => ({
      field,
      before: display(left.get(field)),
      after: display(right.get(field)),
    }));
}
