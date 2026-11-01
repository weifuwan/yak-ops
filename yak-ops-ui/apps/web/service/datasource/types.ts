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
  database?: string;
  schema?: string;
  jdbcUrl?: string;
  displayJdbcUrl?: string;
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

export interface DataSourceConnectionParams {
  /** Provider-native JDBC URL. Oracle uses this field directly. */
  jdbcUrl?: string;
  host?: string;
  port?: number;
  database?: string;
  username: string;
  password: string;
  /** Provider-owned JDBC driver selection id. Only MySQL currently exposes this field. */
  driverId?: string;
  properties: Record<string, string>;
}

export interface DataSourceSavePayload {
  name: string;
  environment: string;
  remark?: string;
  dbType: string;
  connectionParams: DataSourceConnectionParams;
}

export interface DataSourceConnectTestPayload {
  dataSourceId?: DataSourceId;
  dbType: string;
  connectionParams: DataSourceConnectionParams;
}

export interface DataSourceConnectionPropertyKeys {
  acceptedPropertyKeys: string[];
}

/** Safe provider-defined parameter field available to the datasource editor. */
export interface DataSourceOptionDefinition {
  key: string;
  type: string;
  required: boolean;
  defaultValue: string | number | boolean | null;
  allowedValues: string[];
  description: string;
}

export interface DataSourceOptionRules {
  dbType: string;
  options: DataSourceOptionDefinition[];
}

export interface DataSourceValidationError {
  path: string;
  rule: string;
  message: string;
}

export interface DataSourceBatchConnectTestResult {
  dataSourceId: DataSourceId;
  connected: boolean;
}

export interface DataSourceCatalogQuery {
  database?: string;
  schema?: string;
  keyword?: string;
  limit?: number;
}

export interface DataSourceTablePageQuery extends DataSourceCatalogQuery {
  databasePattern?: string;
  tablePattern?: string;
  offset?: number;
}

export interface DataSourceTablePage {
  items: DataSourceCatalogTable[];
  nextOffset: number | null;
}

export interface DataSourcePreviewQuery extends DataSourceCatalogTablePath {
  where?: string;
  limit?: number;
}

export interface DataSourcePreview {
  columns: Array<{ name: string; typeName: string }>;
  rows: Array<Array<string | boolean | null>>;
  truncated: boolean;
}

export interface DataSourceCatalogTablePath {
  database?: string;
  schema?: string;
  table: string;
}

export interface DataSourceCatalogTable {
  database?: string;
  schema?: string;
  name: string;
  type?: string;
  remarks?: string;
}

export interface DataSourceCatalogColumn {
  name: string;
  typeName?: string;
  jdbcType?: number;
  size?: number;
  scale?: number;
  nullable?: boolean;
  ordinalPosition?: number;
  primaryKey?: boolean;
  remarks?: string;
}
