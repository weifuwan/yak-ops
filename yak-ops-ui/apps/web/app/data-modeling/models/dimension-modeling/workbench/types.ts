import type {
  DimensionTableRecord,
  DimensionTreeGrouping,
  DimensionTreeNode,
} from "@/service/data-modeling/models/dimension-modeling";

export type DimensionTabMode = "create" | "edit" | "list";

export type DimensionCreateType =
  | "DIM"
  | "SOURCE"
  | "DIMENSION"
  | "DETAIL"
  | "SUMMARY"
  | "APPLICATION";

export const DIMENSION_CREATE_OPTIONS: Array<{
  type: DimensionCreateType;
  label: string;
  dwType: string;
  group: "dimension" | "table";
}> = [
  { type: "DIM", label: "创建维度", dwType: "PUBLIC_LAYER", group: "dimension" },
  { type: "SOURCE", label: "创建贴源表", dwType: "SOURCE_LAYER", group: "table" },
  { type: "DIMENSION", label: "创建维度表", dwType: "PUBLIC_LAYER", group: "table" },
  { type: "DETAIL", label: "创建明细表", dwType: "PUBLIC_LAYER", group: "table" },
  { type: "SUMMARY", label: "创建汇总表", dwType: "PUBLIC_LAYER", group: "table" },
  { type: "APPLICATION", label: "创建应用表", dwType: "APPLICATION_LAYER", group: "table" },
];

export type DimensionTab = {
  key: string;
  mode: DimensionTabMode;
  createType?: DimensionCreateType;
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
  onCreate: (type: DimensionCreateType) => void;
  onList: () => void;
  onToggle: (key: string) => void;
  onSelect: (node: DimensionTreeNode) => void;
};
