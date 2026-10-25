import type {
  DataSyncParameters,
  DataSyncIncrementalConfig,
  DataSyncSolutionType,
  DataSyncStep,
  DataSyncTableMapping,
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

/** One readable configuration contract; no V2/V3 conversion or protocol fallback. */
export function parseSyncParameters(schemaVersion: number, content: unknown): DataSyncParameters {
  if (schemaVersion !== 4) throw new Error("不支持的同步任务参数版本");
  const value = content as DataSyncParameters;
  const allowed = (object: object, keys: string[]) =>
    Object.keys(object).every((key) => keys.includes(key));
  if (
    !value ||
    typeof value !== "object" ||
    !allowed(value, ["solutionType", "steps", "source", "transform", "sink", "env"]) ||
    !SOLUTIONS.has(value.solutionType) ||
    !Array.isArray(value.steps) ||
    !Array.isArray(value.transform) ||
    value.transform.length !== 0 ||
    !value.env ||
    Object.keys(value.env).length !== 0 ||
    !value.source?.datasourceId ||
    !value.sink?.datasourceId ||
    !Array.isArray(value.source.tables) ||
    !Array.isArray(value.sink.tables) ||
    !value.source.tables.length ||
    value.source.tables.length > 100 ||
    value.source.tables.length !== value.sink.tables.length ||
    !allowed(value.source, ["datasourceId", "tables"]) ||
    !allowed(value.sink, ["datasourceId", "tables", "schemaPolicy"])
  ) {
    throw new Error("不支持的同步任务定义");
  }
  for (const source of value.source.tables) {
    if (
      !source?.id ||
      !source.table ||
      !allowed(source, ["id", "database", "schema", "table", "keyless", "snapshot", "incremental"])
    ) {
      throw new Error("不支持的来源表配置");
    }
  }
  for (const target of value.sink.tables) {
    if (
      !target?.input ||
      !target.table ||
      !target.write?.mode ||
      !allowed(target, ["input", "database", "schema", "table", "write"]) ||
      !allowed(target.write, ["mode", "primaryKeys", "transaction"])
    ) {
      throw new Error("不支持的目标表配置");
    }
  }
  validateSyncParameters(value);
  return value;
}

/** Returns a read-only UI projection, not an alternative persisted table-mapping model. */
export function syncTableMappings(value: DataSyncParameters): DataSyncTableMapping[] {
  const targets = new Map(value.sink.tables.map((target) => [target.input, target]));
  return value.source.tables.map((table) => {
    const target = targets.get(table.id);
    if (!target) throw new Error("找不到目标表映射");
    return {
      source: {
        database: table.database,
        schema: table.schema,
        table: table.table,
        ...(table.keyless ? { keyless: true, chunkKeyColumn: table.snapshot?.chunkKeyColumn } : {}),
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

/** SCHEMA is derived for the current stage viewer and editor; it is never persisted as a data step. */
export function displaySyncSteps(value: DataSyncParameters): DataSyncStep[] {
  return [
    ...(value.sink.schemaPolicy.initial === "CREATE_OR_ALTER" ? (["SCHEMA"] as const) : []),
    ...value.steps,
  ];
}

export function validateSyncParameters(value: DataSyncParameters): void {
  const realtime = solutionSyncType(value.solutionType) === "REALTIME";
  const steps = value.steps;
  if (
    new Set(steps).size !== steps.length ||
    steps.some((step) => step !== "FULL" && step !== "INCREMENTAL") ||
    (realtime && (steps.length !== 2 || steps[0] !== "FULL" || steps[1] !== "INCREMENTAL")) ||
    (!realtime &&
      ![["FULL"], ["INCREMENTAL"], ["FULL", "INCREMENTAL"]].some(
        (candidate) => JSON.stringify(candidate) === JSON.stringify(steps),
      )) ||
    (!supportsMultipleTables(value.solutionType) && value.source.tables.length !== 1)
  )
    throw new Error("不支持的同步步骤或表数量");
  const policy = value.sink.schemaPolicy;
  if (
    !policy ||
    !["VERIFY", "CREATE_OR_ALTER"].includes(policy.initial) ||
    typeof policy.evolution?.enabled !== "boolean" ||
    typeof policy.evolution.allowDropColumns !== "boolean" ||
    Object.keys(policy).some((key) => !["initial", "evolution"].includes(key)) ||
    Object.keys(policy.evolution).some((key) => !["enabled", "allowDropColumns"].includes(key)) ||
    (policy.evolution.allowDropColumns && !policy.evolution.enabled) ||
    (!realtime && policy.evolution.enabled) ||
    (policy.initial === "CREATE_OR_ALTER" && !steps.includes("FULL"))
  )
    throw new Error("不支持的目标结构策略");

  const ids = new Set<string>();
  const sourcePaths = new Set<string>();
  const targetPaths = new Set<string>();
  for (const source of value.source.tables) {
    const sourcePath = JSON.stringify([source.database || "", source.schema || "", source.table]);
    if (!source.id || ids.has(source.id) || sourcePaths.has(sourcePath)) {
      throw new Error("来源表不能重复");
    }
    ids.add(source.id);
    sourcePaths.add(sourcePath);
    if (source.keyless !== undefined && typeof source.keyless !== "boolean")
      throw new Error("无主键配置无效");
    if (source.keyless) {
      if (
        value.solutionType !== "SINGLE_TABLE_REALTIME" ||
        policy.evolution.enabled ||
        !source.snapshot?.chunkKeyColumn?.trim() ||
        Object.keys(source.snapshot).some((key) => key !== "chunkKeyColumn")
      ) {
        throw new Error("无主键快照配置不完整");
      }
    } else if (source.snapshot !== undefined) throw new Error("仅无主键来源可设置快照分片键");
    if (offlineIncremental(value.solutionType, steps)) {
      if (!validIncrementalConfig(source.incremental, !steps.includes("FULL"))) {
        throw new Error("离线增量配置不完整");
      }
    } else if (source.incremental !== undefined) {
      throw new Error("来源增量配置与同步方案不匹配");
    }
  }
  const mapped = new Set<string>();
  for (const target of value.sink.tables) {
    if (!ids.has(target.input) || mapped.has(target.input)) throw new Error("目标表关联无效");
    mapped.add(target.input);
    const path = JSON.stringify([target.database || "", target.schema || "", target.table]);
    if (targetPaths.has(path)) throw new Error("目标表不能重复");
    targetPaths.add(path);
    const source = value.source.tables.find((table) => table.id === target.input)!;
    const write = target.write;
    if (
      !WRITES.has(write.mode) ||
      (write.primaryKeys !== undefined &&
        (!write.primaryKeys.length ||
          new Set(write.primaryKeys).size !== write.primaryKeys.length ||
          write.primaryKeys.some((key) => !key?.trim()))) ||
      (write.primaryKeys && policy.evolution.enabled)
    )
      throw new Error("目标写入主键无效");
    const retract =
      source.keyless &&
      write.mode === "RETRACT" &&
      write.transaction?.mode === "CHECKPOINT_XA" &&
      !write.primaryKeys;
    const keyed =
      source.keyless &&
      write.mode === "UPSERT" &&
      !write.transaction &&
      Boolean(write.primaryKeys?.length);
    if (
      (source.keyless && !retract && !keyed) ||
      (!source.keyless && (write.mode === "RETRACT" || write.transaction !== undefined)) ||
      ((realtime || offlineIncremental(value.solutionType, steps)) &&
        write.mode !== "UPSERT" &&
        !retract)
    ) {
      throw new Error("不支持的目标写入协议");
    }
  }
}

/** Enforces the same current strict contract used for creating a new definition. */
export function readSyncParameters(schemaVersion: number, content: unknown): DataSyncParameters {
  return parseSyncParameters(schemaVersion, content);
}
