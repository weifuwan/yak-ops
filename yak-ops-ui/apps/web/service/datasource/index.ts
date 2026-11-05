import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import { BizError } from "@/service/http/request";
import {
  fromBackendDataSource,
  toBackendConnectionTest,
  toBackendDataSource,
  toBackendType,
  type BackendDataSourceRecord,
} from "./adapter";
import type {
  DataSourceBatchConnectTestResult,
  DataSourceConnectTestPayload,
  DataSourceCatalogColumn,
  DataSourceCatalogQuery,
  DataSourceCatalogTable,
  DataSourceCatalogTablePath,
  DataSourceConnectionPropertyKeys,
  DataSourceOptionRules,
  DataSourceValidationError,
  DataSourceId,
  DataSourcePageParams,
  DataSourcePageResult,
  DataSourceRecord,
  DataSourceSavePayload,
  DataSourceTablePage,
  DataSourceTablePageQuery,
  DataSourcePreview,
  DataSourcePreviewQuery,
} from "./types";

export type * from "./types";
const API = YAK_OPS_API_PREFIX + "/datasources";

type BackendPage = {
  totalList?: BackendDataSourceRecord[];
  total?: number;
  totalPage?: number;
  currentPage?: number;
};

type BackendOption = { label?: string; value?: string | number; disabled?: boolean };
const qs = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") search.set(key, String(value));
  }
  return search.toString();
};

export const datasourceValidationErrors = (error: unknown): DataSourceValidationError[] => {
  if (!(error instanceof BizError) || !Array.isArray(error.response?.data)) return [];
  return error.response.data.filter(
    (issue): issue is DataSourceValidationError =>
      issue != null && typeof issue === "object" &&
      typeof issue.path === "string" && typeof issue.rule === "string",
  );
};

const fetchPage = (pageNo: number, pageSize: number, keyword: string | undefined, signal?: AbortSignal) =>
  HttpUtils.getData<BackendPage>(API + "?" + qs({
    pageNo, pageSize, searchVal: keyword,
  }), { signal });

export async function listDataSources(
  params: DataSourcePageParams,
  signal?: AbortSignal,
): Promise<DataSourcePageResult> {
  const { pageNo, pageSize, keyword, dbType } = params;
  if (!dbType) {
    const page = await fetchPage(pageNo, pageSize, keyword, signal);
    return {
      bizData: (page.totalList || []).map(fromBackendDataSource),
      pagination: { pageNo, pageSize, total: page.total ?? 0, pages: page.totalPage },
    };
  }
  // Java's paged endpoint has no type filter; filter the entire authorized result set,
  // never just the current page (which would produce incorrect totals).
  // Do NOT use /datasources/list?type: that endpoint returns unmasked connectionParams.
  const rows: DataSourceRecord[] = [];
  let current = 1;
  const batchSize = 100;
  while (true) {
    const page = await fetchPage(current, batchSize, keyword, signal);
    rows.push(...(page.totalList || []).map(fromBackendDataSource));
    if (current * batchSize >= (page.total ?? 0) || !page.totalList?.length) break;
    current += 1;
  }
  const matching = rows.filter((row) => row.dbType === dbType);
  return {
    bizData: matching.slice((pageNo - 1) * pageSize, pageNo * pageSize),
    pagination: {
      pageNo, pageSize, total: matching.length,
      pages: Math.ceil(matching.length / pageSize),
    },
  };
}

export async function getDataSource(id: DataSourceId, signal?: AbortSignal): Promise<DataSourceRecord> {
  const raw = await HttpUtils.getData<BackendDataSourceRecord>(API + "/" + encodeURIComponent(id), { signal });
  return fromBackendDataSource(raw);
}

/** Suggested UI parameters; the backend is authoritative on accepted and rejected keys. */
const PROPERTY_HINTS: Record<string, string[]> = {
  MYSQL: ["serverTimezone", "useSSL", "connectTimeout", "socketTimeout", "characterEncoding"],
  POSTGRESQL: ["sslmode", "connectTimeout", "socketTimeout", "ApplicationName"],
  ORACLE: ["oracle.net.CONNECT_TIMEOUT", "oracle.jdbc.ReadTimeout"],
  SQLSERVER: ["encrypt", "trustServerCertificate", "loginTimeout"],
  DB2: ["sslConnection", "currentSchema"],
  DAMENG: ["schema"],
  OCEANBASE: ["connectTimeout", "socketTimeout"],
  CLICKHOUSE: ["socket_timeout", "connect_timeout"],
  STARROCKS: ["connectTimeout", "socketTimeout"],
  DORIS: ["connectTimeout", "socketTimeout"],
};
export const getDataSourceConnectionPropertyKeys = async (
  dbType: string,
): Promise<DataSourceConnectionPropertyKeys> => ({
  acceptedPropertyKeys: PROPERTY_HINTS[toBackendType(dbType)] || [],
});

/** Driver choice is owned by the existing backend plugins, not a frontend dropdown. */
export const getDataSourceOptionRules = async (dbType: string): Promise<DataSourceOptionRules> => ({
  dbType,
  options: dbType === "MYSQL"
    ? [{ key: "driverId", type: "string", required: false, defaultValue: "AUTO",
         allowedValues: ["AUTO"], description: "使用后端已安装的 MySQL JDBC Driver" }]
    : [],
});

export async function createDataSource(payload: DataSourceSavePayload): Promise<void> {
  await HttpUtils.postData<unknown>(API, toBackendDataSource(payload));
}

export async function updateDataSource(id: DataSourceId, payload: DataSourceSavePayload): Promise<void> {
  await HttpUtils.putData<unknown>(API + "/" + encodeURIComponent(id), toBackendDataSource(payload));
}

export async function deleteDataSource(id: DataSourceId): Promise<void> {
  await HttpUtils.deleteData<boolean>(API + "/" + encodeURIComponent(id));
}

/** Bound concurrency prevents a large selection from flooding the standalone server. */
async function processWithLimit<T>(
  items: readonly T[],
  worker: (item: T) => Promise<void>,
): Promise<Array<{ item: T; ok: boolean }>> {
  const output: Array<{ item: T; ok: boolean }> = new Array(items.length);
  let index = 0;
  await Promise.all(Array.from({ length: Math.min(items.length, 3) }, async () => {
    while (index < items.length) {
      const current = index++;
      try {
        await worker(items[current]);
        output[current] = { item: items[current], ok: true };
      } catch {
        output[current] = { item: items[current], ok: false };
      }
    }
  }));
  return output;
}

export async function batchDeleteDataSources(ids: readonly DataSourceId[]): Promise<void> {
  const outcomes = await processWithLimit(ids, deleteDataSource);
  const failed = outcomes.filter((item) => !item.ok).length;
  if (failed) throw new Error("批量删除部分失败：" + failed + "/" + ids.length + "，请刷新列表后重试");
}

export async function batchTestDataSourceConnections(
  ids: readonly DataSourceId[],
): Promise<DataSourceBatchConnectTestResult[]> {
  const outcomes = await processWithLimit(ids, async (id) => {
    await HttpUtils.getData<boolean>(API + "/" + encodeURIComponent(id) + "/connect-test", {
      skipErrorHandler: true,
    });
  });
  return outcomes.map(({ item, ok }) => ({ dataSourceId: item, connected: ok }));
}

export const testDataSourceConnectionWithParams = (
  payload: DataSourceConnectTestPayload,
): Promise<boolean> =>
  HttpUtils.postData<boolean>(API + "/connect",
    toBackendConnectionTest(payload.dbType, payload.connectionParams));

export const testSavedDataSourceConnection = (id: DataSourceId): Promise<boolean> =>
  HttpUtils.getData<boolean>(API + "/" + encodeURIComponent(id) + "/connect-test");

const toOptions = (options: BackendOption[]) =>
  options.filter((option) => !option.disabled).map((option) => String(option.value ?? option.label ?? "")).filter(Boolean);

export const listDataSourceDatabases = async (id: DataSourceId): Promise<string[]> =>
  toOptions(await HttpUtils.getData<BackendOption[]>(API + "/databases?" + qs({ datasourceId: id })));

export async function listDataSourceSchemas(
  _id: DataSourceId, _database?: string, _signal?: AbortSignal,
): Promise<string[]> {
  // DolphinScheduler exposes databases and tables, not a separate schema endpoint.
  return [];
}

export const listDataSourceTables = async (
  id: DataSourceId, query: DataSourceCatalogQuery, signal?: AbortSignal,
): Promise<DataSourceCatalogTable[]> => {
  if (!query.database) return [];
  const options = await HttpUtils.getData<BackendOption[]>(API + "/tables?" +
    qs({ datasourceId: id, database: query.database }), { signal });
  return toOptions(options)
    .filter((name) => !query.keyword || name.toLowerCase().includes(query.keyword.toLowerCase()))
    .slice(0, query.limit ?? Number.MAX_SAFE_INTEGER)
    .map((name) => ({ name, database: query.database, schema: query.schema }));
};

export const listDataSourceColumns = async (
  id: DataSourceId, path: DataSourceCatalogTablePath, signal?: AbortSignal,
): Promise<DataSourceCatalogColumn[]> => {
  if (!path.database || !path.table) return [];
  const options = await HttpUtils.getData<BackendOption[]>(API + "/tableColumns?" +
    qs({ datasourceId: id, database: path.database, tableName: path.table }), { signal });
  return toOptions(options).map((name) => ({ name }));
};

export const pageDataSourceTables = async (
  id: DataSourceId, query: DataSourceTablePageQuery, signal?: AbortSignal,
): Promise<DataSourceTablePage> => {
  const rows = await listDataSourceTables(id, { ...query, limit: undefined }, signal);
  const filtered = rows.filter((row) =>
    !query.tablePattern || row.name.toLowerCase().includes(query.tablePattern.toLowerCase()));
  const offset = query.offset ?? 0;
  const limit = Math.max(1, query.limit ?? 100);
  return {
    items: filtered.slice(offset, offset + limit),
    nextOffset: offset + limit < filtered.length ? offset + limit : null,
  };
};

export const previewDataSourceTable = async (
  _id: DataSourceId, _query: DataSourcePreviewQuery, _signal?: AbortSignal,
): Promise<DataSourcePreview> => {
  throw new Error("当前 Yak Ops 后端尚未提供数据预览接口");
};
