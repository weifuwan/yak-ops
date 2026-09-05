import type {
  DataSourceId,
  DataSourceRecord,
} from '@/services/data-source';
import type { FormInstance } from 'antd';

export type * from '@/services/data-source';

export enum DataSourceOperateType {
  Create = 'CREATE',
  Edit = 'EDIT',
}

export type DataSourceViewMode = 'grid' | 'list';

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
  /** 外部入口已确定 dbType 时传入，弹窗会跳过类型选择页。 */
  dbType?: string;
  /** 从任务配置页创建时可隐藏“上一步”。 */
  hideBack?: boolean;
}

export interface DataSourceModalRef {
  open: (payload: DataSourceModalOpenPayload) => void;
  close: () => void;
}

export interface DynamicDataSourceFormProps {
  dbType: string;
  form: FormInstance<DataSourceFormValues>;
  configForm: FormInstance;
  operateType: DataSourceOperateType;
  initialConfig?: Record<string, unknown>;
}

export interface DataSourceOptionItem {
  label: string;
  value: string;
}

export interface DataSourceCatalogItem {
  onlyDiScript: boolean;
  dbType: string;
  type: string;
  connectorType?: string;
  disabled?: boolean;
  img?: string;
  doc?: {
    reader?: string;
    writer?: string;
  };
}

export interface DataSourceGroup {
  groupKey?: string;
  groupName: string;
  datasourceList: DataSourceCatalogItem[];
}

export interface DataSourcePermissions {
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  canTest: boolean;
}

export interface DataSourceActionState {
  testingId: string;
  editingId: string;
}

export const dataSourceRecordKey = (id?: DataSourceId) => String(id ?? '');
