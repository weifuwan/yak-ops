import { normalizeNodeLocalParams } from "../common/node-resource-params/adapter.ts";
import type { SqlExecutionType, SqlSource, SqlTaskForm } from "./types";

export function readSqlTaskObject(value: unknown): Record<string, unknown> {
  if (typeof value === "string") {
    try {
      const parsed: unknown = JSON.parse(value);
      return parsed && typeof parsed === "object" && !Array.isArray(parsed)
        ? (parsed as Record<string, unknown>)
        : {};
    } catch {
      return {};
    }
  }

  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function readString(value: unknown): string {
  return value === null || value === undefined ? "" : String(value);
}

function readNumberText(value: unknown, fallback: number): string {
  const parsed = Number(value);
  return value !== null && value !== undefined && value !== "" && Number.isFinite(parsed)
    ? String(parsed)
    : String(fallback);
}

function readDatasourceText(value: unknown): string {
  const normalized = readString(value).trim();
  if (!normalized || Number(normalized) === 0) return "";

  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? String(parsed) : normalized;
}

function readStatements(value: unknown): string[] {
  return Array.isArray(value)
    ? value.map((item) => readString(item))
    : [];
}

export function toSqlTaskDbType(value: string): string {
  const normalized = value.trim().toUpperCase();
  return normalized === "POSTGRE_SQL" || normalized === "POSTGRES"
    ? "POSTGRESQL"
    : normalized;
}

export function parseSqlTaskForm(value: unknown): SqlTaskForm {
  const raw = readSqlTaskObject(value);
  const sql = readString(raw.sql);
  const sqlResource = readString(raw.sqlResource);
  const sqlSource: SqlSource =
    raw.sqlSource === "FILE" || ((!raw.sqlSource || raw.sqlSource === "CUSTOM") && !sql && sqlResource)
      ? "FILE"
      : "SCRIPT";

  return {
    type: toSqlTaskDbType(readString(raw.type)),
    datasource: readDatasourceText(raw.datasource),
    sql,
    sqlSource,
    sqlResource,
    sqlType: Number(raw.sqlType) === 1 ? "1" : "0",
    sendEmail: raw.sendEmail === true,
    displayRows: readNumberText(raw.displayRows, 10),
    showType: readString(raw.showType) || "TABLE",
    connParams: readString(raw.connParams),
    preStatements: readStatements(raw.preStatements),
    postStatements: readStatements(raw.postStatements),
    groupId: readNumberText(raw.groupId, 0),
    title: readString(raw.title),
    limit: readNumberText(raw.limit, 0),
    localParams: normalizeNodeLocalParams(raw.localParams),
  };
}

export function buildSqlTaskParams(
  form: SqlTaskForm,
  originalValue?: unknown,
): Record<string, unknown> {
  const original = readSqlTaskObject(originalValue);
  const datasource = Number(form.datasource);
  const displayRows = Number(form.displayRows);
  const limit = Number(form.limit);
  const groupId = Number(form.groupId);

  return {
    ...original,
    type: toSqlTaskDbType(form.type),
    datasource: Number.isFinite(datasource) ? datasource : 0,
    sql: form.sqlSource === "SCRIPT" ? form.sql : "",
    sqlSource: form.sqlSource,
    sqlResource: form.sqlSource === "FILE" ? form.sqlResource.trim() : "",
    sqlType: Number(form.sqlType) === 1 ? 1 : 0,
    sendEmail: form.sendEmail,
    displayRows: Number.isFinite(displayRows) ? displayRows : form.displayRows,
    showType: form.showType || "TABLE",
    connParams: form.connParams,
    preStatements: form.preStatements.map((item) => item.trim()).filter(Boolean),
    postStatements: form.postStatements.map((item) => item.trim()).filter(Boolean),
    groupId: Number.isFinite(groupId) ? groupId : 0,
    title: form.title,
    limit: Number.isFinite(limit) ? limit : form.limit,
    localParams: form.localParams
      .filter((item) => item.prop.trim())
      .map((item) => ({
        ...item,
        prop: item.prop.trim(),
        value: item.value ?? "",
      })),
  };
}

export function validateSqlTaskForm(form: SqlTaskForm): string | undefined {
  const datasource = Number(form.datasource);
  if (!Number.isSafeInteger(datasource) || datasource <= 0 || datasource > 2_147_483_647) {
    return "请选择有效的数据源";
  }
  if (!form.type.trim()) return "所选数据源缺少数据库类型，请重新选择数据源";

  if (form.sqlSource === "SCRIPT" && !form.sql.trim()) {
    return "请先编写 SQL 脚本";
  }
  if (form.sqlSource === "FILE" && !form.sqlResource.trim()) {
    return "请选择 SQL 资源文件";
  }

  const displayRows = Number(form.displayRows);
  if (!Number.isSafeInteger(displayRows) || displayRows < 1) {
    return "日志展示行数必须是大于等于 1 的整数";
  }

  const limit = Number(form.limit);
  if (!Number.isSafeInteger(limit) || limit < 0) {
    return "查询结果限制必须是大于等于 0 的整数";
  }

  if (form.sendEmail && Number(form.sqlType) !== 0) {
    return "结果通知仅支持查询 SQL，请切换为查询模式或关闭结果通知";
  }
  if (form.sendEmail) {
    const groupId = Number(form.groupId);
    if (!Number.isSafeInteger(groupId) || groupId <= 0) {
      return "启用结果通知后，请选择告警组";
    }
  }

  const paramNames = form.localParams.map((item) => item.prop.trim()).filter(Boolean);
  if (new Set(paramNames).size !== paramNames.length) {
    return "本地参数名称不能重复";
  }

  return undefined;
}
