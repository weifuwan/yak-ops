import type { ModelingAssetTreeNode, ModelingRecord } from "../service";

export type StandardModule = "FIELD" | "CODE" | "UNIT";
export type StandardResource = "fieldStandard" | "codeStandard" | "unitStandard";

export interface StandardDirectoryRecord extends ModelingRecord {
  dirName?: string;
  dirType?: string;
  dirModule?: StandardModule;
  parentId?: string | null;
}

export interface FieldStandardRecord extends ModelingRecord {
  stdCode?: string;
  enShortName?: string;
  enName?: string;
  cnName?: string;
  fieldDataType?: string;
  fieldLength?: number | null;
  fieldPrecision?: number | null;
  fieldCanNull?: boolean | null;
  fieldDefaultValue?: string;
  businessDefinition?: string;
  parentStd?: string;
  parentId?: string | null;
  reference?: string;
  createBy?: string;
  updateBy?: string;
}

export interface CodeDetailRecord extends ModelingRecord {
  codeId?: string;
  enShortName?: string;
  enName?: string;
  cnName?: string;
  remark?: string;
}

export interface CodeStandardRecord extends ModelingRecord {
  parentId?: string | null;
  enShortName?: string;
  enName?: string;
  cnName?: string;
  remark?: string;
  detailList?: CodeDetailRecord[];
  createBy?: string;
  updateBy?: string;
}

export interface UnitStandardRecord extends ModelingRecord {
  parentId?: string | null;
  enShortName?: string;
  enName?: string;
  cnName?: string;
  createBy?: string;
  updateBy?: string;
}

export interface DictionaryStandardRecord extends ModelingRecord {
  enShortName?: string;
  enName?: string;
  cnName?: string;
  flag?: number;
  createBy?: string;
  updateBy?: string;
}

export type StandardTreeNode = ModelingAssetTreeNode;

export function getStandardResource(module: StandardModule): StandardResource {
  if (module === "FIELD") return "fieldStandard";
  if (module === "CODE") return "codeStandard";
  return "unitStandard";
}

export function getStandardTitle(module: StandardModule): string {
  if (module === "FIELD") return "字段标准";
  if (module === "CODE") return "标准代码";
  return "度量单位";
}

export function getStandardDescription(module: StandardModule): string {
  if (module === "FIELD") return "统一维护字段编码、命名、数据类型、长度精度和业务定义";
  if (module === "CODE") return "维护标准代码集及编码取值、名称和业务含义";
  return "统一维护指标使用的货币、数量、比例、排名和时间等度量单位";
}

export function getDirectoryTypeLabel(type?: string): string {
  if (type === "FOLDER") return "文件夹";
  if (type === "STANDARD_SET") return "标准集";
  if (type === "STANDARD") return "字段标准目录";
  return type || "-";
}

export function isDirectoryNode(node?: StandardTreeNode | null): boolean {
  return Boolean(node && ["FOLDER", "STANDARD_SET", "STANDARD"].includes(node.type));
}

export function getNodeRecordName(node?: StandardTreeNode | null): string {
  return node?.title || "当前记录";
}
