export type DataSourceId = string;

export interface DataSourceRecord {
  projectCode?: string;
  id?: DataSourceId;
  name?: string;
  dbType?: string;
  database?: string;
  schema?: string;
  jdbcUrl?: string;
  displayJdbcUrl?: string;
  environment?: string;
  environmentName?: string;
  remark?: string;
  /** Detail only. Sensitive values are returned as ******. */
  originalJson?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DataSourcePageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  dbType?: string;
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
  compatibleMode?: "mysql" | "oracle";
  privateKey?: string;
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

export interface DataSourceDriverProperty {
  name: string;
  description: string;
  defaultValue: string | null;
  required: boolean;
  choices: string[];
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
  total: number;
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

export type CatalogNodeType =
  | "DATASOURCE" | "CATALOG" | "DATABASE" | "SCHEMA"
  | "TABLE_GROUP" | "VIEW_GROUP" | "MATERIALIZED_VIEW_GROUP"
  | "FUNCTION_GROUP" | "PROCEDURE_GROUP" | "TRIGGER_GROUP" | "SEQUENCE_GROUP"
  | "TABLE" | "VIEW" | "MATERIALIZED_VIEW" | "FUNCTION" | "PROCEDURE" | "TRIGGER" | "SEQUENCE"
  | "COLUMN_GROUP" | "COLUMN" | "INDEX_GROUP" | "INDEX";

export type CatalogObjectType =
  | "TABLE" | "VIEW" | "MATERIALIZED_VIEW" | "FUNCTION" | "PROCEDURE" | "TRIGGER" | "SEQUENCE";

export interface CatalogPath {
  catalog?: string;
  database?: string;
  schema?: string;
  objectName?: string;
  objectType?: CatalogObjectType;
}

export interface CatalogCapabilities {
  namespaceLevels: Array<"CATALOG" | "DATABASE" | "SCHEMA">;
  objectTypes: CatalogObjectType[];
  features: Array<"COLUMNS" | "INDEXES" | "PRIMARY_KEYS" | "FOREIGN_KEYS" | "PARTITIONS" | "DDL" | "TABLE_STATISTICS" | "COMMENTS">;
}

export interface CatalogTreeNode {
  id: string;
  name: string;
  nodeType: CatalogNodeType;
  objectType?: CatalogObjectType | null;
  hasChildren: boolean;
  path: CatalogPath;
  metadata?: Record<string, unknown>;
}

export interface CatalogChildrenPage {
  items: CatalogTreeNode[];
  total: number;
  pageNo: number;
  pageSize: number;
  hasMore: boolean;
}

export interface CatalogChildrenQuery extends CatalogPath {
  nodeType: CatalogNodeType;
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface CatalogObject {
  name: string;
  type: CatalogObjectType;
  comment?: string | null;
  path: CatalogPath;
  hasChildren: boolean;
  primaryKeys: string[];
}

export interface CatalogIndex {
  name: string;
  columns: string[];
  unique: boolean;
  primary: boolean;
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
  primaryKeys: string[];
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
