import type { DataSourceRecord, DataSourceSavePayload } from "../model/types";
import type {
  DataSourceConnectionFormValues,
  DataSourceFormValues,
} from "./types";

let cachedOriginalJson: string | undefined;
let cachedOriginalConfig: Record<string, unknown> = {};

export function filterDataSourceList(
  list: DataSourceRecord[],
  keyword: string,
): DataSourceRecord[] {
  const searchKeyword = keyword.trim().toLowerCase();
  if (!searchKeyword) return list;

  return list.filter((item) => {
    const name = item.name?.toLowerCase() || '';
    const jdbcUrl = item.jdbcUrl?.toLowerCase() || '';
    const environmentName = item.environmentName?.toLowerCase() || '';
    const dbType = String(item.dbType || '').toLowerCase();
    return (
      name.includes(searchKeyword) ||
      jdbcUrl.includes(searchKeyword) ||
      environmentName.includes(searchKeyword) ||
      dbType.includes(searchKeyword)
    );
  });
}

interface KeyValueRow {
  key?: unknown;
  value?: unknown;
}

/** 将高级配置编辑器的行数据恢复成后端沿用的 JSON 对象协议。 */
export function serializeKeyValueRows(value: unknown): Record<string, string> {
  if (!Array.isArray(value)) {
    if (value && typeof value === 'object') {
      return Object.fromEntries(
        Object.entries(value as Record<string, unknown>).map(([key, itemValue]) => [
          key,
          itemValue === undefined || itemValue === null ? '' : String(itemValue),
        ]),
      );
    }
    return {};
  }

  const result: Record<string, string> = {};
  value.forEach((item) => {
    if (!item || typeof item !== 'object') return;
    const row = item as KeyValueRow;
    const key = String(row.key ?? '').trim();
    if (!key) return;
    result[key] = row.value === undefined || row.value === null ? '' : String(row.value);
  });
  return result;
}

/**
 * 动态表单内部可以使用更适合 UI 的值形态，但发给数据源插件的连接协议保持稳定。
 */
export function normalizeConnectionFormValues(
  connectionValues: DataSourceConnectionFormValues,
): DataSourceConnectionFormValues {
  const normalized = { ...connectionValues };
  if ('properties' in normalized) {
    normalized.properties = serializeKeyValueRows(normalized.properties);
  }
  return normalized;
}

export function buildSubmitPayload(
  dbType: string,
  basicValues: DataSourceFormValues,
  connectionValues: DataSourceConnectionFormValues,
): DataSourceSavePayload {
  return {
    dbType,
    ...basicValues,
    connectionParams: JSON.stringify({
      ...normalizeConnectionFormValues(connectionValues),
      dbType,
    }),
  };
}

export function parseOriginalJson(
  originalJson?: string,
): Record<string, unknown> {
  if (!originalJson) return {};
  if (originalJson === cachedOriginalJson) return cachedOriginalConfig;

  try {
    const parsed = JSON.parse(originalJson);
    cachedOriginalJson = originalJson;
    cachedOriginalConfig =
      parsed && typeof parsed === 'object' && !Array.isArray(parsed)
        ? parsed
        : {};
    return cachedOriginalConfig;
  } catch {
    cachedOriginalJson = originalJson;
    cachedOriginalConfig = {};
    return cachedOriginalConfig;
  }
}
