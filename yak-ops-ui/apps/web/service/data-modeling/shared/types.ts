export interface ModelingRecord {
  id?: string;
  createTime?: string;
  updateTime?: string;
  [key: string]: unknown;
}

export interface ModelingVersionRecord {
  id: string;
  versionNumber: number;
  versionType: string;
  updateTime?: string;
  updateBy?: string;
}

export interface ModelingAssetTreeNode {
  id: string;
  key: string;
  value: string;
  title: string;
  parentId?: string;
  type: string;
  children: ModelingAssetTreeNode[];
}

export interface ModelingSelectOption {
  value: string;
  label: string;
}
