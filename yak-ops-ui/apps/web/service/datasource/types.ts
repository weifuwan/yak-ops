export type DataSourceId = number | string;

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

export interface DynamicFormFieldRule {
  required?: boolean;
  pattern?: string;
  min?: number;
  max?: number;
  message: string;
}

export type DynamicFormVisibilityOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'IN'
  | 'NOT_IN'
  | 'TRUTHY'
  | 'FALSY';

/** 多个条件默认使用 AND 语义。 */
export interface DynamicFormVisibilityCondition {
  field?: string;
  operator?: DynamicFormVisibilityOperator;
  value?: unknown;
  values?: unknown[];
}

/** JDBC URL 与结构化 Host / Port / Database 字段之间的双向联动描述。 */
export interface DynamicFormJdbcUrlLinkage {
  template: string;
  hostField?: string;
  portField?: string;
  databaseField?: string;
  preserveSuffix?: boolean;
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

export type DynamicFormFieldType =
  | 'INPUT'
  | 'PASSWORD'
  | 'SELECT'
  | 'NUMBER'
  | 'SWITCH'
  | 'TEXTAREA'
  | 'CUSTOM_SELECT'
  | 'DRIVER'
  | 'SSH'
  | 'JDBC_URL';

export interface DynamicFormField {
  key: string;
  label: string;
  type: DynamicFormFieldType;
  placeholder?: string;
  options?: Array<{ label: string; value: string | number }>;
  defaultValue?: unknown;
  rules?: DynamicFormFieldRule[];
  dependsOn?: string[];
  visibleWhen?:
    | DynamicFormVisibilityCondition
    | DynamicFormVisibilityCondition[];
  urlLinkage?: DynamicFormJdbcUrlLinkage;
}

export interface DynamicFormSection {
  key: string;
  title: string;
  description?: string;
  collapsible?: boolean;
  defaultExpanded?: boolean;
  fields: DynamicFormField[];
}

export interface DynamicFormSchemaResponse {
  pluginType?: string;
  sections?: DynamicFormSection[];
  formFields?: DynamicFormField[];
  installRequired?: boolean;
  installHint?: string;
}

export interface DriverUploadResult {
  fileName?: string;
  path?: string;
}

