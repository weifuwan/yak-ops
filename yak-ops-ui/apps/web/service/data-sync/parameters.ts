import type {
  DataSyncParameters,
  DataSyncIncrementalConfig,
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
const WRITES = new Set(["APPEND", "UPSERT", "RETRACT"]);

export const solutionSyncType = (type: DataSyncSolutionType) =>
  type === "SINGLE_TABLE_REALTIME" || type === "DATABASE_REALTIME" ? "REALTIME" : "OFFLINE";

export const supportsMultipleTables = (type: DataSyncSolutionType) =>
  type === "DATABASE_OFFLINE" || type === "DATABASE_REALTIME";

export const offlineIncremental = (solution: DataSyncSolutionType, steps: DataSyncStep[]) =>
  solutionSyncType(solution) === "OFFLINE" && steps.includes("INCREMENTAL");

export const validIncrementalConfig = (
  value: DataSyncIncrementalConfig | undefined,
  requireStart: boolean,
) => {
  if (
    !value ||
    !["TIMESTAMP", "INCREASING"].includes(value.mode) ||
    !value.column.trim() ||
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
  const realtime = value && solutionSyncType(value.solutionType) === "REALTIME";
  if (
    value?.schemaChangesEnabled !== undefined &&
    (typeof value.schemaChangesEnabled !== "boolean" || !realtime)
  ) {
    throw new Error("结构变更配置只适用于实时任务");
  }
  if (
    value?.allowDropColumns !== undefined &&
    (typeof value.allowDropColumns !== "boolean" || !realtime)
  ) {
    throw new Error("删除目标字段只能在实时任务中配置");
  }
  if (value?.allowDropColumns && !value.schemaChangesEnabled) {
    throw new Error("删除目标字段需要开启同步结构变更");
  }
  const steps = value?.steps || [];
  const expected: DataSyncStep[] = realtime
    ? ["FULL", "INCREMENTAL"]
    : (["FULL", "INCREMENTAL"].filter((step) =>
        steps.includes(step as DataSyncStep),
      ) as DataSyncStep[]);
  if (steps.includes("SCHEMA")) expected.unshift("SCHEMA");
  if (expected.includes("SCHEMA") && !expected.includes("FULL")) {
    throw new Error("结构迁移必须包含全量初始化");
  }
  if (
    !value ||
    !SOLUTIONS.has(value.solutionType) ||
    !Array.isArray(value.steps) ||
    !expected.some((step) => step === "FULL" || step === "INCREMENTAL") ||
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
    const keyless = mapping?.source?.keyless === true;
    const validRetract =
      keyless &&
      mapping.writeMode === "RETRACT" &&
      mapping.source.xa === true &&
      mapping.target.primaryKeys === undefined;
    const validKeyedWrite =
      keyless &&
      mapping.writeMode === "UPSERT" &&
      mapping.source.xa === undefined &&
      Boolean(mapping.target.primaryKeys?.length);
    const invalidTransaction = keyless
      ? !validRetract && !validKeyedWrite
      : mapping.source.xa !== undefined;
    if (
      (mapping?.source?.keyless !== undefined && typeof mapping.source.keyless !== "boolean") ||
      (mapping?.source?.chunkKeyColumn !== undefined &&
        (typeof mapping.source.chunkKeyColumn !== "string" ||
          !mapping.source.chunkKeyColumn.trim() ||
          mapping.source.chunkKeyColumn.length > 128)) ||
      (mapping.target?.primaryKeys !== undefined &&
        (!Array.isArray(mapping.target.primaryKeys) ||
          mapping.target.primaryKeys.length === 0 ||
          new Set(mapping.target.primaryKeys).size !== mapping.target.primaryKeys.length ||
          mapping.target.primaryKeys.some((key) => typeof key !== "string" || !key.trim()))) ||
      invalidTransaction ||
      (keyless &&
        (value.solutionType !== "SINGLE_TABLE_REALTIME" ||
          value.schemaChangesEnabled !== false ||
          value.allowDropColumns === true ||
          !mapping.source.chunkKeyColumn)) ||
      (!keyless &&
        (mapping?.source?.chunkKeyColumn !== undefined || mapping.writeMode === "RETRACT")) ||
      !mapping?.source?.table ||
      !mapping.target?.table ||
      !WRITES.has(mapping.writeMode) ||
      ((solutionSyncType(value.solutionType) === "REALTIME" ||
        offlineIncremental(value.solutionType, value.steps)) &&
        mapping.writeMode !== "UPSERT" &&
        !(keyless && mapping.writeMode === "RETRACT")) ||
      (offlineIncremental(value.solutionType, value.steps)
        ? !validIncrementalConfig(mapping.incremental, !value.steps.includes("FULL"))
        : mapping.incremental !== undefined)
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
