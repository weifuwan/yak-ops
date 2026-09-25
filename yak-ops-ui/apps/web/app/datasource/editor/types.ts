import type { DataSourceFormInstance } from "./form-runtime";

import type { DataSourceRecord, SshTunnelConfigValue } from "../types";

export enum DataSourceOperateType {
  Create = "CREATE",
  Edit = "EDIT",
}

export interface DataSourceFormValues {
  name: string;
  environment: string;
  remark?: string;
}

export interface DataSourceConnectionFormValues {
  host?: string;
  port?: number;
  database?: string;
  schema?: string;
  username?: string;
  password?: string;
  jdbcUrl?: string;
  sshTunnel?: SshTunnelConfigValue;
  driverClassName?: string;
  properties?: unknown;
}

export interface DataSourceModalOpenPayload {
  operateType: DataSourceOperateType;
  currentRecord?: DataSourceRecord;
  onSuccess?: () => void;
  dbType?: string;
  hideBack?: boolean;
}

export interface DataSourceModalRef {
  open: (payload: DataSourceModalOpenPayload) => void;
  close: () => void;
}

export interface DataSourceConnectionFormProps {
  dbType: string;
  form: DataSourceFormInstance<DataSourceFormValues>;
  configForm: DataSourceFormInstance<DataSourceConnectionFormValues>;
  operateType: DataSourceOperateType;
  initialConfig?: Record<string, unknown>;
}
