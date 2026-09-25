import HttpUtils from "@/service/http/HttpUtils";

import type {
  DataSourceConnectTestPayload,
  DataSourceId,
  DataSourcePageParams,
  DataSourcePageResult,
  DataSourceRecord,
  DataSourceSavePayload,
  DataSourceSummary,
  DynamicFormSchemaResponse,
} from "./types";

export type * from "./types";

const DATA_SOURCE_API_PREFIX = "/api/v1/data-source";

const queryString = (params: Record<string, unknown>) => {
  const search = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).length > 0) {
      search.set(key, String(value));
    }
  });

  const result = search.toString();
  return result ? `?${result}` : "";
};

export const listDataSources = (
  params: DataSourcePageParams,
): Promise<DataSourcePageResult> =>
  HttpUtils.postData<DataSourcePageResult>(
    `${DATA_SOURCE_API_PREFIX}/page`,
    params,
  );

export const getDataSourceSummary = (): Promise<DataSourceSummary> =>
  HttpUtils.getData<DataSourceSummary>(`${DATA_SOURCE_API_PREFIX}/summary`);

export const getDataSource = (id: DataSourceId): Promise<DataSourceRecord> =>
  HttpUtils.getData<DataSourceRecord>(`${DATA_SOURCE_API_PREFIX}/${id}`);

export const createDataSource = async (
  payload: DataSourceSavePayload,
): Promise<void> => {
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

export const testDataSourceConnection = async (
  id: DataSourceId,
): Promise<void> => {
  await HttpUtils.postData<boolean>(
    `${DATA_SOURCE_API_PREFIX}/${id}/connect-test`,
    {},
  );
};

export const testDataSourceConnectionWithParams = (
  payload: DataSourceConnectTestPayload,
): Promise<boolean> =>
  HttpUtils.postData<boolean>(
    `${DATA_SOURCE_API_PREFIX}/connect-test-with-param`,
    payload,
  );

export const getDataSourcePluginConfig = (
  pluginType: string,
): Promise<DynamicFormSchemaResponse> =>
  HttpUtils.getData<DynamicFormSchemaResponse>(
    `${DATA_SOURCE_API_PREFIX}/plugin/config${queryString({ pluginType })}`,
  );
