import type {
  DataSyncDml,
  DataSyncMappingTemplate,
  DataSyncSourceOptions,
  DataSyncTablePath,
  DataSyncTableRead,
  DataSyncTableSnapshot,
  DataSyncTableWrite,
  DataSyncTypeMapping,
} from "./types";

const object = (value: unknown): value is Record<string, unknown> =>
  Boolean(value && typeof value === "object" && !Array.isArray(value));
const keys = (value: object, allowed: string[]) =>
  Object.keys(value).every((key) => allowed.includes(key));
const optionalText = (value: unknown) =>
  value === undefined || (typeof value === "string" && Boolean(value.trim()));

export function validateTableRead(value: DataSyncTableRead | undefined): void {
  if (
    value !== undefined &&
    (!object(value) ||
      !keys(value, ["where", "splitKey"]) ||
      !optionalText(value.where) ||
      !optionalText(value.splitKey))
  )
    throw new Error("来源过滤或切分键配置无效");
}

export function validateTableSnapshot(value: DataSyncTableSnapshot | undefined): void {
  if (
    value !== undefined &&
    (!object(value) ||
      !keys(value, ["chunkKeyColumn", "skip", "where"]) ||
      !optionalText(value.chunkKeyColumn) ||
      !optionalText(value.where) ||
      (value.skip !== undefined && typeof value.skip !== "boolean"))
  )
    throw new Error("全量同步配置无效");
  if (value?.skip && value.where) throw new Error("跳过全量同步不能同时设置全量条件");
}

export function validateTableDml(value: DataSyncDml | undefined): void {
  if (
    value !== undefined &&
    (!object(value) ||
      !keys(value, ["events"]) ||
      !Array.isArray(value.events) ||
      !value.events.length ||
      value.events.some((event) => !["INSERT", "UPDATE", "DELETE"].includes(event)) ||
      new Set(value.events).size !== value.events.length)
  )
    throw new Error("至少保留一种有效的 DML 事件");
}

export function validateSqlHooks(write: DataSyncTableWrite): void {
  for (const statements of [write.preSql, write.postSql])
    if (
      statements !== undefined &&
      (!Array.isArray(statements) ||
        statements.some((statement) => typeof statement !== "string" || !statement.trim()))
    )
      throw new Error("准备语句和完成语句不能为空");
}

export function validateSourceOptions(value: DataSyncSourceOptions | undefined): void {
  if (value === undefined) return;
  if (!object(value) || !keys(value, ["fetchSize", "queryTimeoutSeconds"]))
    throw new Error("读取引擎参数无效");
  for (const [key, option] of Object.entries(value))
    if (
      option !== undefined &&
      (typeof option !== "number" ||
        !Number.isInteger(option) ||
        option < 1 ||
        option > (key === "fetchSize" ? 10000 : 300))
    )
      throw new Error(`${key === "fetchSize" ? "读取批次大小" : "查询超时"}超出允许范围`);
}

export function validateBatchSize(value: number | undefined): void {
  if (value !== undefined && (!Number.isInteger(value) || value < 1 || value > 10000))
    throw new Error("写入批次大小必须为 1 到 10000 的整数");
}

export function validateTypeMappings(value: DataSyncTypeMapping[] | undefined): void {
  if (value === undefined) return;
  if (
    !Array.isArray(value) ||
    value.some(
      (rule) =>
        !object(rule) ||
        !keys(rule, ["sourceType", "targetType"]) ||
        !optionalText(rule.sourceType) ||
        !optionalText(rule.targetType) ||
        !rule.sourceType ||
        !rule.targetType,
    ) ||
    new Set(value.map((rule) => rule.sourceType)).size !== value.length
  )
    throw new Error("字段类型映射不完整或来源类型重复");
}

/** Expand only the three named path placeholders; templates never evaluate code. */
export function applyTableTemplate(
  template: string | undefined,
  source: DataSyncTablePath,
): string | undefined {
  if (!template) return undefined;
  if (/\$\{(?!database\}|schema\}|table\})/.test(template))
    throw new Error("表映射模板只支持 database、schema、table 变量");
  return template.replace(
    /\$\{(database|schema|table)\}/g,
    (_match, key: "database" | "schema" | "table") => source[key] || "",
  );
}

export function templateTarget(
  template: DataSyncMappingTemplate,
  source: DataSyncTablePath,
): DataSyncTablePath {
  const table = applyTableTemplate(template.table, source);
  if (!table?.trim()) throw new Error("请输入目标表映射模板");
  return {
    database: applyTableTemplate(template.database, source) || undefined,
    schema: applyTableTemplate(template.schema, source) || undefined,
    table,
  };
}
