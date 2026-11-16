import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Skeleton,
} from "@yak-ops/yak-ui";
import {
  BookOpen,
  ChevronRight,
  Database,
  Folder,
  Layers3,
  List,
  Plus,
  RefreshCw,
  Search,
  Table2,
  Tags,
} from "lucide-react";

import { businessTypeApi } from "@/service/data-modeling/planning/business-type";
import { dataDomainApi } from "@/service/data-modeling/planning/common-layer/data-domain";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import { DIMENSION_WAREHOUSE_OPTIONS } from "../hooks/use-dimension-tree";
import type { DimensionSidebarProps } from "../types";

type ScopeOption = { value: string; label: string };

const GROUP_OPTIONS = [
  { value: "BUSINESS_TYPE", label: "业务分类" },
  { value: "DATA_DOMAIN", label: "数据域" },
] as const;

function recordLabel(record: ModelingRecord, fields: string[]) {
  for (const field of fields) {
    const value = record[field];
    if (value !== undefined && value !== null && String(value).trim()) {
      return String(value);
    }
  }
  return String(record.id || "未命名");
}

function DimensionNodeIcon({ type }: { type: string }) {
  if (type === "ROOT_NODE") return <Layers3 size={14} className="shrink-0 text-[#3a3d51]" />;
  if (type === "BUSINESS_TYPE") return <Tags size={14} className="shrink-0 text-[#3a3d51]" />;
  if (type === "DATA_DOMAIN") return <Database size={14} className="shrink-0 text-[#3a3d51]" />;
  if (type === "MART") return <Layers3 size={14} className="shrink-0 text-[#3a3d51]" />;
  if (type === "SUBJECT") return <BookOpen size={14} className="shrink-0 text-[#3a3d51]" />;
  if (type === "FOLDER" || type === "BUSINESS_PROCESS") {
    return <Folder size={14} className="shrink-0 text-[#3a3d51]" />;
  }
  return <Table2 size={14} className="shrink-0 text-[#3a3d51]" />;
}

type TreeRowsProps = {
  nodes: NonNullable<DimensionSidebarProps["root"]>[];
  depth: number;
  expandedKeys: Set<string>;
  selectedTreeKey: string;
  onToggle: (key: string) => void;
  onSelect: (node: NonNullable<DimensionSidebarProps["root"]>) => void;
};

function DimensionTreeRows({
  nodes,
  depth,
  expandedKeys,
  selectedTreeKey,
  onToggle,
  onSelect,
}: TreeRowsProps) {
  return (
    <div>
      {nodes.map((node) => {
        const children = node.children || [];
        const hasChildren = children.length > 0;
        const expanded = expandedKeys.has(node.key);
        const selected = selectedTreeKey === node.key;

        return (
          <div key={node.key}>
            <button
              type="button"
              title={node.title}
              onClick={() => {
                if (hasChildren) onToggle(node.key);
                else if (node.leaf) onSelect(node);
              }}
              className={[
                "group flex min-h-8 w-full min-w-0 items-center gap-1.5 border-b border-[#eceff780] pr-2 text-left text-xs transition-colors",
                selected
                  ? "bg-[#DFE7FF] text-[#252a35]"
                  : "bg-white text-[#353b48] hover:bg-[#f5f7fb]",
              ].join(" ")}
              style={{ paddingLeft: 8 + depth * 18 }}
            >
              <span className="flex size-4 shrink-0 items-center justify-center text-[#8b95a5]">
                {hasChildren ? (
                  <ChevronRight
                    size={12}
                    className={[
                      "transition-transform duration-150",
                      expanded ? "rotate-90" : "",
                    ].join(" ")}
                  />
                ) : null}
              </span>
              <DimensionNodeIcon type={node.type} />
              <span className="min-w-0 flex-1 truncate">{node.title}</span>
              {!node.leaf && node.total !== undefined ? (
                <span className="shrink-0 text-[10px] text-[#929aaa]">
                  ({node.total})
                </span>
              ) : null}
            </button>
            {hasChildren && expanded ? (
              <DimensionTreeRows
                nodes={children}
                depth={depth + 1}
                expandedKeys={expandedKeys}
                selectedTreeKey={selectedTreeKey}
                onToggle={onToggle}
                onSelect={onSelect}
              />
            ) : null}
          </div>
        );
      })}
    </div>
  );
}

export function DimensionSidebar({
  projectCode,
  dwType,
  onDwTypeChange,
  groupBy,
  onGroupByChange,
  scopeValue,
  onScopeValueChange,
  keyword,
  onKeywordChange,
  root,
  expandedKeys,
  selectedTreeKey,
  loading,
  initialLoading,
  error,
  onRefresh,
  onCreate,
  onList,
  onToggle,
  onSelect,
}: DimensionSidebarProps) {
  const showScopeFilter = dwType !== "APPLICATION_LAYER";
  const businessTypesQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "dimension-tree-business-types", projectCode],
    queryFn: () => businessTypeApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode && showScopeFilter),
  });
  const dataDomainsQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "dimension-tree-data-domains", projectCode],
    queryFn: () => dataDomainApi.all<ModelingRecord>(projectCode),
    enabled: Boolean(projectCode && showScopeFilter),
  });

  const scopeOptions = useMemo<ScopeOption[]>(() => {
    const records = groupBy === "BUSINESS_TYPE"
      ? businessTypesQuery.data || []
      : dataDomainsQuery.data || [];
    return records.map((record) => ({
      value: String(record.id || ""),
      label: groupBy === "BUSINESS_TYPE"
        ? recordLabel(record, ["businessName", "cnName", "enName"])
        : recordLabel(record, ["cnName", "domainName", "enName"]),
    })).filter((option) => option.value);
  }, [groupBy, businessTypesQuery.data, dataDomainsQuery.data]);

  const scopePlaceholder = groupBy === "BUSINESS_TYPE" ? "请选择业务分类" : "请选择数据域";
  const scopeLoading = groupBy === "BUSINESS_TYPE"
    ? businessTypesQuery.isFetching
    : dataDomainsQuery.isFetching;

  return (
    <aside className="flex h-full w-[292px] min-w-[260px] max-w-[360px] shrink-0 flex-col border-r border-[var(--yak-components-panel-border)] bg-white">
      <div className="flex shrink-0 items-center justify-between px-3 pb-2 pt-3">
        <span className="text-sm font-semibold text-[var(--yak-components-panel-text)]">
          维度建模
        </span>
        <div className="flex items-center gap-0.5">
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title="新建维度表" onClick={onCreate}>
            <Plus size={14} />
          </Button>
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title="列表模式" onClick={onList}>
            <List size={14} />
          </Button>
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title="刷新目录" isDisabled={loading} onClick={onRefresh}>
            <RefreshCw size={13} className={loading ? "animate-spin" : ""} />
          </Button>
        </div>
      </div>

      <div className="flex w-full shrink-0 flex-col gap-2 px-2.5 pb-2">
        <div
          role="radiogroup"
          aria-label="数仓分层"
          className="flex h-6 w-full overflow-hidden rounded-[4px] border border-[#d7dce5] bg-white"
        >
          {DIMENSION_WAREHOUSE_OPTIONS.map((option, index) => {
            const checked = dwType === option.value;
            return (
              <button
                key={option.value}
                type="button"
                role="radio"
                aria-checked={checked}
                tabIndex={checked ? 0 : -1}
                onClick={() => onDwTypeChange(option.value)}
                className={[
                  "relative flex h-full min-w-0 flex-1 items-center justify-center px-1 text-[11px] leading-none transition-colors",
                  index > 0 ? "border-l border-[#d7dce5]" : "",
                  checked
                    ? "z-[1] bg-[#f4f7ff] font-medium text-[#4169df] shadow-[inset_0_0_0_1px_#cbd7ff]"
                    : "text-[#606875] hover:bg-[#f8f9fc]",
                ].join(" ")}
              >
                <span className="truncate">{option.label}</span>
              </button>
            );
          })}
        </div>

        {showScopeFilter ? (
          <div className="flex h-7 min-w-0 gap-0">
            <Select
              size="small"
              value={groupBy}
              onValueChange={(value) => onGroupByChange(String(value || "BUSINESS_TYPE") as "BUSINESS_TYPE" | "DATA_DOMAIN")}
            >
              <SelectTrigger
                aria-label="目录分组类型"
                variant="outlined"
                className="!h-7 !w-[88px] !min-w-[88px] !shrink-0 !rounded-r-none !px-2"
              >
                <SelectValue>
                  {(value) => GROUP_OPTIONS.find((item) => item.value === value)?.label || "业务分类"}
                </SelectValue>
              </SelectTrigger>
              <SelectContent>
                {GROUP_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    <SelectItemText>{option.label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select
              size="small"
              value={scopeValue || undefined}
              onValueChange={(value) => onScopeValueChange(String(value || ""))}
            >
              <SelectTrigger
                aria-label={scopePlaceholder}
                variant="outlined"
                className="!h-7 !min-w-0 !flex-1 !rounded-l-none !border-l-0 !px-2"
              >
                <SelectValue placeholder={scopeLoading ? "正在加载..." : scopePlaceholder}>
                  {(value) => scopeOptions.find((option) => option.value === value)?.label || ""}
                </SelectValue>
              </SelectTrigger>
              <SelectContent emptyContent="暂无可选目录">
                {scopeOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    <SelectItemText>{option.label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        ) : null}

        <div className="relative">
          <Search size={13} className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
          <Input
            size="small"
            variant="outlined"
            value={keyword}
            placeholder="搜索维度或维度表"
            className="!h-7 !rounded-[4px] !pl-8 !pr-2 text-[11px]"
            onChange={(event) => onKeywordChange(event.target.value)}
          />
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto pb-3">
        {initialLoading ? (
          <div role="status" aria-label="正在加载目录" className="flex flex-col gap-2 px-3 py-3">
            {[92, 124, 88, 108, 76, 116].map((width, index) => (
              <div key={index} className="flex min-h-6 items-center gap-2" style={{ paddingLeft: index % 3 === 0 ? 0 : 18 }}>
                <Skeleton width={12} height={12} />
                <Skeleton width={width} height={10} />
              </div>
            ))}
          </div>
        ) : error ? (
          <div className="flex flex-col items-center gap-2 px-3 py-8 text-xs text-[var(--yak-components-muted-text)]">
            <span>目录加载失败</span>
            <Button size="sm" variant="secondary" onClick={onRefresh}>重新加载</Button>
          </div>
        ) : root ? (
          <>
            <DimensionTreeRows
              nodes={[root]}
              depth={0}
              expandedKeys={expandedKeys}
              selectedTreeKey={selectedTreeKey}
              onToggle={onToggle}
              onSelect={onSelect}
            />
            {!root.children?.length ? (
              <div className="px-3 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
                暂无维度表，点击上方 + 新建
              </div>
            ) : null}
          </>
        ) : (
          <div className="px-3 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
            暂无维度表，点击上方 + 新建
          </div>
        )}
      </div>
    </aside>
  );
}

export default DimensionSidebar;
