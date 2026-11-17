import type {
  DimensionTableRecord,
  DimensionTreeGrouping,
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
  groupBy: DimensionTreeGrouping;
  onGroupByChange: (value: DimensionTreeGrouping) => void;
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
  emptyText: string;
  onRefresh: () => void;
  onCreate: () => void;
  onList: () => void;
  onToggle: (key: string) => void;
  onSelect: (node: DimensionTreeNode) => void;
};
