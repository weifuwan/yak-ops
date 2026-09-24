export type * from "@/service/datasource/types";

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
