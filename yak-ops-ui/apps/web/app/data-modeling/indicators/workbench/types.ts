import type { MouseEvent as ReactMouseEvent } from "react";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type { IndicatorOptionResource as IndicatorOptionResourceType, IndicatorResourceKind } from "@/service/data-modeling/indicators";

export type IndicatorOptionResource = IndicatorOptionResourceType;
export type IndicatorValidation = (
  values: Record<string, string>,
  optionsByResource: Partial<Record<IndicatorOptionResource, ModelingRecord[]>>,
) => string | undefined;

export type IndicatorKind = IndicatorResourceKind;
export type IndicatorMode = "create" | "edit" | "list";

export type IndicatorFieldOption = { value: string; label: string; disabled?: boolean };
export type IndicatorField = {
  key: string;
  label: string;
  section?: string;
  placeholder?: string;
  required?: boolean;
  multiline?: boolean;
  readOnly?: boolean;
  options?: IndicatorFieldOption[];
  optionsSource?: IndicatorOptionResource;
  optionLabelField?: string;
  optionLabelPrefixField?: string;
  optionFilterField?: string;
  includeOptionValues?: string[];
  excludeOptionValues?: string[];
};
export type IndicatorColumn = { key: string; title: string; width?: number };
export type IndicatorColumnFormat = "indicatorName" | "status" | "dwType" | "computeMode" | "modifierType" | "relation";
export type IndicatorListFilterKey = "businessTypeId" | "domainId" | "categoryId" | "martId" | "subjectId";
export type IndicatorListFilter = {
  key: IndicatorListFilterKey;
  paramKey: string;
  label: string;
  optionsSource: IndicatorOptionResource;
  optionLabelField?: string;
  dwType?: string;
};
export type IndicatorDefinition = {
  key: IndicatorKind;
  title: string;
  description: string;
  fields: IndicatorField[];
  columns: IndicatorColumn[];
  versioned: boolean;
  listFilters?: IndicatorListFilter[];
  filterByDwType?: boolean;
  columnFormats?: Record<string, IndicatorColumnFormat>;
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

export type IndicatorTreeNodeType = "root" | "businessType" | "dataDomain" | "businessProcess" | "dataMart" | "subjectDomain" | "asset" | "group";

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

export type IndicatorGroupBy = "BUSINESS_TYPE" | "DATA_DOMAIN" | "DATA_MART" | "SUBJECT_DOMAIN";
export type ModifierScopeType = "BUSINESS_TYPE" | "DATA_DOMAIN";
export type IndicatorDeleteTarget = { kind: IndicatorKind; record: ModelingRecord };
export type IndicatorTreeContextMenuState = { x: number; y: number; record: ModelingRecord } | null;
export type IndicatorTabContextMenuState = { tabKey: string; x: number; y: number };
export type PendingTabClose = { keys: string[]; dirtyTitles: string[] };
export type IndicatorPageProps = {
  projectCode: string;
  active: boolean;
  dwType: string;
  onDwTypeChange: (value: string) => void;
  onCreate: () => void;
  onList: () => void;
  onSelect: (record: ModelingRecord) => void;
  onContextMenu: (event: ReactMouseEvent<HTMLButtonElement>, node: IndicatorTreeNode) => void;
};
export type IndicatorEditorFieldsProps = {
  projectCode: string;
  values: Record<string, string>;
  recordId: string;
  optionsByResource: Partial<Record<IndicatorOptionResource, ModelingRecord[]>>;
  optionsFailedResources?: IndicatorOptionResource[];
  optionsLoading: boolean;
  onChange: (key: string, value: string) => void;
  onChangeMany: (values: Record<string, string>) => void;
};
