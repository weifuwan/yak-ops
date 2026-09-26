export type DataSourceId = string;

export type DataSourceConnectionStatus = "UNKNOWN" | "CONNECTED" | "DISCONNECTED" | string;

export interface PaginationInfo {
  pageNo: number;
  pageSize: number;
  total: number;
  pages?: number;
}

export interface DataSourceRecord {
  id?: DataSourceId;
  name?: string;
  dbType?: string;
  jdbcUrl?: string;
  environment?: string;
  environmentName?: string;
  connStatus?: DataSourceConnectionStatus;
  remark?: string;
  /** Detail only. Sensitive values are returned as ******. */
  originalJson?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DataSourcePageResult {
  bizData: DataSourceRecord[];
  pagination: PaginationInfo;
}

export interface DataSourcePageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  dbType?: string;
  connStatus?: string;
}

export interface DataSourceSavePayload {
  name: string;
  environment: string;
  remark?: string;
  dbType: string;
  connectionParams: string;
}

export interface DataSourceConnectTestPayload {
  dataSourceId?: DataSourceId;
  dbType?: string;
  connJson: string;
}

export interface DataSourceBatchConnectTestResult {
  dataSourceId: DataSourceId;
  connected: boolean;
}
