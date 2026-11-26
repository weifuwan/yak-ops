import HttpUtils from "@/service/http/HttpUtils";
import { modelingBasePath } from "../shared/modeling-http";
import type { ModelingRecord } from "../shared/types";
import type { PageInfo } from "@/service/http/page-info";

export type DimensionWarehouseType =
  | "SOURCE_LAYER"
  | "PUBLIC_LAYER"
  | "APPLICATION_LAYER"
  | "NONE_LAYER";

export type DimensionTreeGrouping =
  | "BUSINESS_TYPE"
  | "DATA_DOMAIN"
  | "DATA_MART"
  | "SUBJECT_DOMAIN";

export type DimensionTreeNode = {
  key: string;
  title: string;
  parentId?: string | null;
  type: string;
  modelId?: string;
  total?: number;
  leaf: boolean;
  updateTime?: string;
  searchText?: string;
  children?: DimensionTreeNode[];
};

export type DimensionTableRecord = ModelingRecord & {
  id?: string;
  dwhId?: string;
  dwhName?: string;
  busTypeId?: string;
  domainId?: string;
  busProcessId?: string;
  busProcessIds?: string[];
  martId?: string;
  subjectId?: string;
  martOrSubject?: string;
  enShortName?: string;
  tableName?: string;
  cnName?: string;
  timePeriodId?: string;
  dimId?: string;
  modifier?: string;
  nameRuleId?: string;
  tableType?: string;
  storagePolicy?: string;
  lifeCycle?: number;
  director?: string;
  remark?: string;
  submit?: boolean | string;
  currentVersion?: number;
  updateBy?: string;
};

export type DimensionFieldRecord = {
  id?: string;
  tempId?: string;
  tableId?: string;
  fieldName: string;
  cnName?: string;
  dataType: string;
  length?: number;
  fieldPrecision?: number;
  ordinal?: number;
  fieldScale?: number;
  datetimePrecision?: number;
  defaultValue?: string;
  remark?: string;
  primaryFlag?: boolean;
  notNull?: boolean;
  sourceTb?: string;
  sourceField?: string;
  fieldType?: string;
  unitId?: string;
  fieldStdId?: string;
  codeStdId?: string;
};


export type DorisTableModel = "UNIQUE" | "DUPLICATE";
export type DorisPublishMode = "INCREMENTAL" | "DROP_RECREATE";
export type DorisFieldTypeOption = {
  code: string;
  label: string;
  parameterType: "NONE" | "LENGTH" | "DECIMAL" | "DATETIME_PRECISION";
  maxLength?: number;
  maxPrecision?: number;
  maxScale?: number;
};
export type DorisPublishRequest = {
  datasourceId: number;
  tableModel: DorisTableModel;
  publishMode: DorisPublishMode;
  publishRemark: string;
  replicationNum?: number | null;
  confirmDrop?: boolean;
  confirmDropColumns?: boolean;
  confirmedDdlHash?: string;
};
export type DorisPublishRecord = {
  id?: string;
  tableId?: string;
  versionNumber?: number;
  datasourceId?: number;
  datasourceName?: string;
  targetDatabase?: string;
  targetTable?: string;
  tableModel?: DorisTableModel;
  publishMode?: DorisPublishMode;
  publishRemark?: string;
  ddl?: string;
  ddlHash?: string;
  physicalSchema?: string;
  noChanges?: boolean;
  replicationNum?: number;
  availableBackendCount?: number;
  replicationAuto?: boolean;
  replicationWarning?: string;
  targetTableExists?: boolean;
  addedColumns?: string[];
  droppedColumns?: string[];
  requiresConfirmation?: boolean;
  schemaDrift?: boolean;
  status?: string;
  errorMessage?: string;
  executionDurationMs?: number;
  startedAt?: string;
  finishedAt?: string;
  createTime?: string;
  createBy?: string;
};

export type DimensionVersionRecord = {
  key: string;
  versionNumber: number;
  updateBy?: string;
  updateTime?: string;
};

const resourcePath = (projectCode: string) =>
  modelingBasePath(projectCode) + "/dimensions";

export const dimensionModelingApi = {
  tree(projectCode: string, params: Record<string, unknown>) {
    return HttpUtils.postData<DimensionTreeNode[]>(
      resourcePath(projectCode) + "/tree",
      params,
    );
  },

  page(projectCode: string, params: Record<string, unknown>) {
    return HttpUtils.postData<PageInfo<DimensionTableRecord>>(
      resourcePath(projectCode) + "/page",
      params,
    );
  },

  all(projectCode: string, params: Record<string, unknown> = {}) {
    return HttpUtils.postData<DimensionTableRecord[]>(
      resourcePath(projectCode) + "/all",
      params,
    );
  },

  getById(projectCode: string, id: string) {
    return HttpUtils.getData<DimensionTableRecord>(
      resourcePath(projectCode) + "/" + encodeURIComponent(id),
    );
  },

  create(projectCode: string, payload: Record<string, unknown>) {
    return HttpUtils.postData<DimensionTableRecord>(
      resourcePath(projectCode),
      payload,
    );
  },

  update(projectCode: string, payload: Record<string, unknown>) {
    return HttpUtils.putData<DimensionTableRecord>(
      resourcePath(projectCode),
      payload,
    );
  },

  submit(projectCode: string, id: string) {
    return HttpUtils.putData<DimensionTableRecord>(
      resourcePath(projectCode) + "/" + encodeURIComponent(id) + "/submit",
      {},
    );
  },

  remove(projectCode: string, id: string) {
    return HttpUtils.deleteData<string>(
      resourcePath(projectCode) + "/" + encodeURIComponent(id),
    );
  },

  fields(projectCode: string, id: string) {
    return HttpUtils.getData<DimensionFieldRecord[]>(
      resourcePath(projectCode) + "/" + encodeURIComponent(id) + "/fields",
    );
  },

  replaceFields(projectCode: string, id: string, fields: DimensionFieldRecord[]) {
    return HttpUtils.putData<DimensionFieldRecord[]>(
      resourcePath(projectCode) + "/" + encodeURIComponent(id) + "/fields",
      fields,
    );
  },

  versionHistory(projectCode: string, id: string) {
    return HttpUtils.getData<DimensionVersionRecord[]>(
      resourcePath(projectCode) + "/version-history/" + encodeURIComponent(id),
    );
  },


  dorisFieldTypes(projectCode: string) {
    return HttpUtils.getData<DorisFieldTypeOption[]>(resourcePath(projectCode) + "/options/doris/field-types");
  },

  previewDorisDdl(projectCode: string, id: string, payload: DorisPublishRequest) {
    return HttpUtils.postData<DorisPublishRecord>(resourcePath(projectCode) + "/" + encodeURIComponent(id) + "/ddl/preview", payload);
  },

  publishDoris(projectCode: string, id: string, payload: DorisPublishRequest) {
    return HttpUtils.postData<DorisPublishRecord>(resourcePath(projectCode) + "/" + encodeURIComponent(id) + "/publish", payload);
  },

  dorisPublishHistory(projectCode: string, id: string) {
    return HttpUtils.getData<DorisPublishRecord[]>(resourcePath(projectCode) + "/" + encodeURIComponent(id) + "/publish-history");
  },

  rollback(projectCode: string, id: string, targetVersion: number) {
    return HttpUtils.postData<DimensionTableRecord>(
      resourcePath(projectCode) + "/rollback/" + encodeURIComponent(id)
        + "?targetVersion=" + encodeURIComponent(String(targetVersion)),
      {},
    );
  },
};

export type { ModelingRecord };
