import type {
  DimensionTableRecord,
  DimensionTreeNode,
} from "@/service/data-modeling/models/dimension-modeling";

export type DimensionTabMode = "create" | "edit" | "list";

export type DimensionTab = {
  key: string;
  mode: DimensionTabMode;
  dwType?: string;
  label: string;
  record?: DimensionTableRecord;
  dirty?: boolean;
};

export type DimensionSidebarProps = {
  projectCode: string;
  dwType: string;
  onDwTypeChange: (value: string) => void;
  groupBy: "BUSINESS_TYPE" | "DATA_DOMAIN";
  onGroupByChange: (value: "BUSINESS_TYPE" | "DATA_DOMAIN") => void;
  scopeValue: string;
  onScopeValueChange: (value: string) => void;
  keyword: string;
  onKeywordChange: (value: string) => void;
  root: DimensionTreeNode | null;
  expandedKeys: Set<string>;
  selectedTreeKey: string;
  loading: boolean;
  initialLoading: boolean;
  error: boolean;
  onRefresh: () => void;
  onCreate: () => void;
  onList: () => void;
  onToggle: (key: string) => void;
  onSelect: (node: DimensionTreeNode) => void;
};
