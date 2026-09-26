import HttpUtils from "@/service/http/HttpUtils";

import type {
  DataSourceBatchConnectTestResult,
  DataSourceConnectTestPayload,
  DataSourceConnectionPropertyKeys,
  DataSourceId,
  DataSourcePageParams,
  DataSourcePageResult,
  DataSourceRecord,
  DataSourceSavePayload,
} from "./types";

export type * from "./types";

const DATA_SOURCE_API_PREFIX = "/api/v1/data-source";

export const listDataSources = (params: DataSourcePageParams): Promise<DataSourcePageResult> =>
  HttpUtils.postData<DataSourcePageResult>(`${DATA_SOURCE_API_PREFIX}/page`, params);

export const getDataSource = (id: DataSourceId): Promise<DataSourceRecord> =>
  HttpUtils.getData<DataSourceRecord>(`${DATA_SOURCE_API_PREFIX}/${id}`);

export const getDataSourceConnectionPropertyKeys = (
  dbType: string,
): Promise<DataSourceConnectionPropertyKeys> =>
  HttpUtils.getData<DataSourceConnectionPropertyKeys>(
    `${DATA_SOURCE_API_PREFIX}/connection-property-keys?dbType=${encodeURIComponent(dbType)}`,
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
