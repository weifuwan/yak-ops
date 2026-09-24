import type { DataSourceFormInstance } from "./formRuntime";

import type { DataSourceRecord } from "../model/types";

export enum DataSourceOperateType {
  Create = "CREATE",
  Edit = "EDIT",
}

export interface DataSourceFormValues {
  name: string;
  environment: string;
  remark?: string;
}

export type DataSourceConnectionFormValues = Record<string, unknown>;

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

export interface DynamicDataSourceFormProps {
  dbType: string;
  form: DataSourceFormInstance<DataSourceFormValues>;
  configForm: DataSourceFormInstance;
  operateType: DataSourceOperateType;
  initialConfig?: Record<string, unknown>;
}
