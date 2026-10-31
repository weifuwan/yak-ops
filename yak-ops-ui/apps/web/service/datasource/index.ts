import HttpUtils from "@/service/http/HttpUtils";
import { BizError } from "@/service/http/request";

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
} from "./types";

export type * from "./types";

const DATA_SOURCE_API_PREFIX = "/api/v1/data-source";

/** Extracts field validation issues from datasource HTTP failures without exposing transport details. */
export const datasourceValidationErrors = (error: unknown): DataSourceValidationError[] => {
  if (!(error instanceof BizError) || !Array.isArray(error.response?.data)) return [];
  return error.response.data.filter(
    (issue): issue is DataSourceValidationError =>
      issue != null &&
      typeof issue === "object" &&
      typeof issue.path === "string" &&
      typeof issue.rule === "string",
  );
};

export const listDataSources = (
  params: DataSourcePageParams,
  signal?: AbortSignal,
): Promise<DataSourcePageResult> =>
  HttpUtils.postData<DataSourcePageResult>(`${DATA_SOURCE_API_PREFIX}/page`, params, { signal });

export const getDataSource = (id: DataSourceId, signal?: AbortSignal): Promise<DataSourceRecord> =>
  HttpUtils.getData<DataSourceRecord>(`${DATA_SOURCE_API_PREFIX}/${id}`, { signal });

export const getDataSourceConnectionPropertyKeys = (
  dbType: string,
): Promise<DataSourceConnectionPropertyKeys> =>
  HttpUtils.getData<DataSourceConnectionPropertyKeys>(
    `${DATA_SOURCE_API_PREFIX}/connection-property-keys?dbType=${encodeURIComponent(dbType)}`,
  );

export const getDataSourceOptionRules = (dbType: string): Promise<DataSourceOptionRules> =>
  HttpUtils.getData<DataSourceOptionRules>(
    `${DATA_SOURCE_API_PREFIX}/option-rules?dbType=${encodeURIComponent(dbType)}`,
  );

export const createDataSource = async (payload: DataSourceSavePayload): Promise<void> => {
  await HttpUtils.postData<boolean>(DATA_SOURCE_API_PREFIX, payload);
};

export const updateDataSource = async (
  id: DataSourceId,
  payload: DataSourceSavePayload,
): Promise<void> => {
  await HttpUtils.putData<boolean>(`${DATA_SOURCE_API_PREFIX}/${id}`, payload);
};

export const deleteDataSource = async (id: DataSourceId): Promise<void> => {
  await HttpUtils.deleteData<boolean>(`${DATA_SOURCE_API_PREFIX}/${id}`);
};

export const batchDeleteDataSources = async (ids: readonly DataSourceId[]): Promise<void> => {
  await HttpUtils.postData<boolean>(`${DATA_SOURCE_API_PREFIX}/batch-delete`, { ids });
};

export const batchTestDataSourceConnections = (
  ids: readonly DataSourceId[],
): Promise<DataSourceBatchConnectTestResult[]> =>
  HttpUtils.postData<DataSourceBatchConnectTestResult[]>(
    `${DATA_SOURCE_API_PREFIX}/batch-connect-test`,
    { ids },
  );

export const testDataSourceConnectionWithParams = (
  payload: DataSourceConnectTestPayload,
): Promise<boolean> =>
  HttpUtils.postData<boolean>(`${DATA_SOURCE_API_PREFIX}/connect-test-with-param`, payload);

export const listDataSourceDatabases = (id: DataSourceId): Promise<string[]> =>
  HttpUtils.getData<string[]>(`${DATA_SOURCE_API_PREFIX}/${id}/catalog/databases`);

export const listDataSourceSchemas = (
  id: DataSourceId,
  database?: string,
  signal?: AbortSignal,
): Promise<string[]> => {
  const query = database ? `?database=${encodeURIComponent(database)}` : "";
  return HttpUtils.getData<string[]>(`${DATA_SOURCE_API_PREFIX}/${id}/catalog/schemas${query}`, {
    signal,
  });
};

export const listDataSourceTables = (
  id: DataSourceId,
  query: DataSourceCatalogQuery,
  signal?: AbortSignal,
): Promise<DataSourceCatalogTable[]> =>
  HttpUtils.postData<DataSourceCatalogTable[]>(
    `${DATA_SOURCE_API_PREFIX}/${id}/catalog/tables`,
    query,
    { signal },
  );

export const listDataSourceColumns = (
  id: DataSourceId,
  path: DataSourceCatalogTablePath,
  signal?: AbortSignal,
): Promise<DataSourceCatalogColumn[]> =>
  HttpUtils.postData<DataSourceCatalogColumn[]>(
    `${DATA_SOURCE_API_PREFIX}/${id}/catalog/columns`,
    path,
    { signal },
  );
