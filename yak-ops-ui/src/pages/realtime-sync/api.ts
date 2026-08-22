import { request } from '@umijs/max';
import type {
  ApiResponse,
  CdcPipelineSpec,
  ComputeEnvironmentOption,
  DataSourceCatalogColumn,
  DataSourceCatalogTable,
  DataSourceOption,
  RealtimeDeployment,
  RealtimeEvent,
  RealtimeJob,
  RealtimeJobPage,
  RealtimeObservability,
  RealtimeRuntimeLog,
  ReleaseState,
  RuntimeCapabilities,
} from './types';

const PREFIX = '/api/v1/realtime-sync';
const DATA_SOURCE_PREFIX = '/api/v1/data-source';

export interface RealtimePageQuery {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  id?: number;
  releaseState?: ReleaseState;
  stateGroup?: 'RUNNING' | 'STOPPED' | 'ABNORMAL';
}

interface RealtimeDefinitionPayload {
  name: string;
  description?: string;
  runtimeEnvironmentId: number;
  spec: CdcPipelineSpec;
}

export const realtimeApi = {
  page: (params: RealtimePageQuery) =>
    request<ApiResponse<RealtimeJobPage>>(PREFIX, {
      params,
    }),
  detail: (id: number) => request<ApiResponse<RealtimeJob>>(`${PREFIX}/${id}`),
  createBasic: (payload: { name: string; description?: string; runtimeEnvironmentId: number }) =>
    request<ApiResponse<number>>(PREFIX, { method: 'POST', data: payload }),
  create: (payload: RealtimeDefinitionPayload) =>
    request<ApiResponse<number>>(`${PREFIX}/draft`, { method: 'POST', data: payload }),
  update: (id: number, payload: RealtimeDefinitionPayload) =>
    request<ApiResponse<number>>(`${PREFIX}/${id}`, { method: 'PUT', data: payload }),
  action: (id: number, action: 'publish' | 'validate' | 'start' | 'stop' | 'restart' | 'reconcile') =>
    request<ApiResponse<RealtimeDeployment | boolean>>(`${PREFIX}/${id}/${action}`, {
      method: 'POST',
      headers:
        action === 'start' || action === 'restart'
          ? { 'Idempotency-Key': crypto.randomUUID() }
          : undefined,
    }),
  remove: (id: number) => request<ApiResponse<boolean>>(`${PREFIX}/${id}`, { method: 'DELETE' }),
  events: (id: number) => request<ApiResponse<RealtimeEvent[]>>(`${PREFIX}/${id}/events`),
  observability: (id: number) =>
    request<ApiResponse<RealtimeObservability>>(`${PREFIX}/${id}/observability`),
  submissionLog: (id: number, tail = 500) =>
    request<ApiResponse<{ logs: string }>>(`${PREFIX}/${id}/logs/submission`, { params: { tail } }),
  runtimeLog: (id: number, maxExceptions = 50) =>
    request<ApiResponse<RealtimeRuntimeLog>>(`${PREFIX}/${id}/logs/runtime`, {
      params: { maxExceptions },
    }),
  capabilities: (environmentId: number) =>
    request<ApiResponse<RuntimeCapabilities>>(`${PREFIX}/runtime/capabilities`, {
      params: { environmentId },
    }),
  environments: () =>
    request<ApiResponse<ComputeEnvironmentOption[]>>('/api/v1/compute-environments'),
  dataSources: () => request<ApiResponse<DataSourceOption[]>>(`${DATA_SOURCE_PREFIX}/option`),
  catalogTables: (dataSourceId: number) =>
    request<ApiResponse<DataSourceCatalogTable[]>>(`${DATA_SOURCE_PREFIX}/catalog/${dataSourceId}/tables`),
  catalogColumns: (dataSourceId: number, table: DataSourceCatalogTable) =>
    request<ApiResponse<DataSourceCatalogColumn[]>>(`${DATA_SOURCE_PREFIX}/catalog/${dataSourceId}/columns`, {
      params: {
        database: table.database || undefined,
        schema: table.schema || undefined,
        table: table.name,
      },
    }),
};
