export type DataSourceId = string;

export type DataSourceConnectionStatus =
  | 'UNKNOWN'
  | 'CONNECTED'
  | 'DISCONNECTED'
  | string;

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
  /** 仅详情接口返回，敏感字段使用 ****** 回显。 */
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
  dbType?: string;
  name?: string;
  keyword?: string;
  environment?: string;
  connStatus?: string;
}

export interface DataSourceSummary {
  total: number;
  connected: number;
  disconnected: number;
  unknown: number;
  environmentCount: number;
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

export type SshAuthType = 'PASSWORD' | 'PRIVATE_KEY';

export interface SshTunnelConfigValue {
  enabled?: boolean;
  host?: string;
  port?: number;
  username?: string;
  authType?: SshAuthType;
  password?: string;
  privateKey?: string;
  passphrase?: string;
  strictHostKeyChecking?: boolean;
  knownHosts?: string;
}
