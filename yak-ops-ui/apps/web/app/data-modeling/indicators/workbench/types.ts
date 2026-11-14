import type { ModelingRecord, ModelingResourceKey } from "../../service";

export type IndicatorKind = "atomIndicator" | "derivativeIndicator" | "composeIndicator" | "modifier" | "timePeriod";
export type IndicatorMode = "create" | "edit" | "list";

export type IndicatorFieldOption = { value: string; label: string };
export type IndicatorField = {
  key: string;
  label: string;
  section?: string;
  placeholder?: string;
  required?: boolean;
  multiline?: boolean;
  readOnly?: boolean;
  options?: IndicatorFieldOption[];
  optionsSource?: ModelingResourceKey;
  optionLabelField?: string;
};
export type IndicatorColumn = { key: string; title: string; width?: number };
export type IndicatorDefinition = {
  key: IndicatorKind;
  title: string;
  description: string;
  fields: IndicatorField[];
  columns: IndicatorColumn[];
  versioned: boolean;
};

export type IndicatorTab = {
  key: string;
  kind: IndicatorKind;
  mode: IndicatorMode;
  label: string;
  recordId?: string;
  record?: ModelingRecord;
  draftValues?: Record<string, string>;
  dirty?: boolean;
};

export type IndicatorTreeNodeType = "root" | "businessType" | "dataDomain" | "businessProcess" | "asset" | "group";

export type IndicatorTreeNode = {
  key: string;
  id: string;
  title: string;
  kind: "group" | "asset";
  nodeType?: IndicatorTreeNodeType;
  count?: number;
  record?: ModelingRecord;
  children: IndicatorTreeNode[];
};

export type IndicatorGroupBy = "BUSINESS_TYPE" | "DATA_DOMAIN";
export type IndicatorDeleteTarget = { kind: IndicatorKind; record: ModelingRecord };
export type IndicatorTreeContextMenuState = { x: number; y: number; record: ModelingRecord } | null;
export type IndicatorTabContextMenuState = { tabKey: string; x: number; y: number };
export type PendingTabClose = { keys: string[]; dirtyTitles: string[] };
