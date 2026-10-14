import type {
  DataSyncParameters,
  DataSyncSolutionType,
  DataSyncStep,
  SyncPipelineParameters,
} from "./types";

const SOLUTIONS = new Set([
  "SINGLE_TABLE_OFFLINE",
  "SINGLE_TABLE_REALTIME",
  "DATABASE_OFFLINE",
  "DATABASE_REALTIME",
]);
const WRITES = new Set(["APPEND", "UPSERT"]);

export const solutionSyncType = (type: DataSyncSolutionType) =>
  type === "SINGLE_TABLE_REALTIME" || type === "DATABASE_REALTIME" ? "REALTIME" : "OFFLINE";

export const supportsMultipleTables = (type: DataSyncSolutionType) =>
  type === "DATABASE_OFFLINE" || type === "DATABASE_REALTIME";

/** Interprets the pinned parameter version; unrecognized formats are never guessed as offline. */
export function readSyncParameters(schemaVersion: number, content: string): DataSyncParameters {
  const parsed = JSON.parse(content);
  let value: DataSyncParameters;
  if (schemaVersion === 2) {
    const pipeline = parsed as SyncPipelineParameters;
    if (
      !pipeline ||
      "solutionType" in pipeline ||
      !["JDBC", "MYSQL_CDC"].includes(pipeline.source?.type) ||
      pipeline.sink?.type !== "JDBC" ||
      (pipeline.transform !== undefined &&
        (!Array.isArray(pipeline.transform) || pipeline.transform.length !== 0))
    ) {
      throw new Error("不支持的同步任务定义");
    }
    const continuous = pipeline.source.type === "MYSQL_CDC";
    value = {
      solutionType: continuous ? "SINGLE_TABLE_REALTIME" : "SINGLE_TABLE_OFFLINE",
      steps: continuous ? ["FULL", "INCREMENTAL"] : ["FULL"],
      source: { datasourceId: pipeline.source.datasourceId },
      sink: { datasourceId: pipeline.sink.datasourceId },
      tableSelection: { mode: "EXPLICIT" },
      tableMappings: [
        {
          source: {
            database: pipeline.source.database,
            schema: pipeline.source.schema,
            table: pipeline.source.table,
          },
          target: {
            database: pipeline.sink.database,
            schema: pipeline.sink.schema,
            table: pipeline.sink.table,
          },
          writeMode: pipeline.sink.writeMode,
        },
      ],
    };
  } else if (schemaVersion === 3) {
    value = parsed as DataSyncParameters;
    if (parsed?.source?.type !== undefined || parsed?.sink?.type !== undefined) {
      throw new Error("同步任务参数格式与版本不匹配");
    }
  } else {
    throw new Error("不支持的同步任务参数版本");
  }
  const expected: DataSyncStep[] =
    solutionSyncType(value?.solutionType) === "REALTIME" ? ["FULL", "INCREMENTAL"] : ["FULL"];
  if (
    !value ||
    !SOLUTIONS.has(value.solutionType) ||
    !Array.isArray(value.steps) ||
    value.steps.length !== expected.length ||
    new Set(value.steps).size !== expected.length ||
    expected.some((step) => !value.steps.includes(step)) ||
    value.tableSelection?.mode !== "EXPLICIT" ||
    !Array.isArray(value.tableMappings) ||
    value.tableMappings.length < 1 ||
    value.tableMappings.length > 100 ||
    (!supportsMultipleTables(value.solutionType) && value.tableMappings.length !== 1) ||
    !value.source?.datasourceId ||
    !value.sink?.datasourceId
  ) {
    throw new Error("不支持的同步方案或表映射");
  }
  const sources = new Set<string>();
  const targets = new Set<string>();
  for (const mapping of value.tableMappings) {
    if (
      !mapping?.source?.table ||
      !mapping.target?.table ||
      !WRITES.has(mapping.writeMode) ||
      (solutionSyncType(value.solutionType) === "REALTIME" && mapping.writeMode !== "UPSERT")
    ) {
      throw new Error("不支持的同步目标写入配置");
    }
    const sourceKey = JSON.stringify([
      mapping.source.database || "",
      mapping.source.schema || "",
      mapping.source.table,
    ]);
    const targetKey = JSON.stringify([
      mapping.target.database || "",
      mapping.target.schema || "",
      mapping.target.table,
    ]);
    if (sources.has(sourceKey) || targets.has(targetKey)) throw new Error("来源表和去向表不能重复");
    sources.add(sourceKey);
    targets.add(targetKey);
  }
  return value;
}
