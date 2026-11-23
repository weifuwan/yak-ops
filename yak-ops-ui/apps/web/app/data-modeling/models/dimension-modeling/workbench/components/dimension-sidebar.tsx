import { useEffect, useMemo, useState, type KeyboardEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectSearch,
  SelectTrigger,
  SelectValue,
  Skeleton,
  TreeSelect,
  type TreeSelectOption,
} from "@yak-ops/yak-ui";
import {
  Database,
  Layers3,
  List,
  Plus,
  RefreshCw,
  Search,
  Table2,
} from "lucide-react";

import { ModelTypeIcon, isModelTypeIconType } from "../../../../components/model-type-icon";
import { PlanningIcon } from "../../../../planning/components/icons";
import { businessTypeApi, type BusinessTypeTreeNode } from "@/service/data-modeling/planning/business-type";
import { dataDomainApi } from "@/service/data-modeling/planning/common-layer/data-domain";
import { dataMartApi } from "@/service/data-modeling/planning/application-layer/data-mart";
import { subjectDomainApi } from "@/service/data-modeling/planning/application-layer/subject-domain";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type {
  DimensionTreeGrouping,
  DimensionTreeNode,
} from "@/service/data-modeling/models/dimension-modeling";
import { DIMENSION_WAREHOUSE_OPTIONS } from "../hooks/use-dimension-tree";
import { DIMENSION_CREATE_OPTIONS, type DimensionSidebarProps } from "../types";
import { findBusinessTypeTreeOption, mapBusinessTypeTreeOption } from "../utils/business-type-tree-options";

type ScopeOption = { value: string; label: string };

const SOURCE_SCOPE_OPTIONS: Array<{ value: DimensionTreeGrouping; label: string }> = [
  { value: "BUSINESS_TYPE", label: "业务分类" },
];

const COMMON_SCOPE_OPTIONS: Array<{ value: DimensionTreeGrouping; label: string }> = [
  { value: "DATA_DOMAIN", label: "数据域" },
  { value: "BUSINESS_TYPE", label: "业务分类" },
];

const APPLICATION_SCOPE_OPTIONS: Array<{ value: DimensionTreeGrouping; label: string }> = [
  { value: "DATA_MART", label: "数据集市" },
  { value: "SUBJECT_DOMAIN", label: "主题域" },
];

function ScopeIcon({ scope }: { scope: DimensionTreeGrouping }) {
  if (scope === "DATA_MART") return <Database size={15} className="text-[#3a3d51]" />;
  if (scope === "SUBJECT_DOMAIN") return <Layers3 size={15} className="text-[#3a3d51]" />;
  if (scope === "BUSINESS_TYPE") {
    return <PlanningIcon name="business-type" size={15} className="text-[#3a3d51]" />;
  }
  return <PlanningIcon name="data-domain" size={15} className="text-[#3a3d51]" />;
}

function recordLabel(record: ModelingRecord, fields: string[]) {
  for (const field of fields) {
    const value = record[field];
    if (value !== undefined && value !== null && String(value).trim()) {
      return String(value);
    }
  }
  return "未命名";
}

function ExpandArrowIcon({ expanded }: { expanded: boolean }) {
  return (
    <svg
      viewBox="0 0 1024 1024"
      width="8"
      height="8"
      aria-hidden="true"
      fill="currentColor"
      className={[
        "shrink-0 transition-transform duration-150 ease-out motion-reduce:transition-none",
        expanded ? "rotate-90" : "rotate-0",
      ].join(" ")}
    >
      <path d="M215.578947 0v1024l592.842106-512L215.578947 0z" />
    </svg>
  );
}

function FolderIcon({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 1024 1024"
      width="14"
      height="14"
      aria-hidden="true"
      className={className}
      fill="currentColor"
    >
      <path d="M42.688 106.688H404.48l85.376 106.624h491.52V896H42.624V106.688zM128 192v181.312h768V298.688H448.832L363.52 192H128z m768 266.688H128v352h768v-352z" />
    </svg>
  );
}

function DimensionNodeIcon({ type }: { type: string }) {
  if (isModelTypeIconType(type)) {
    return <ModelTypeIcon type={type} size={14} className="shrink-0" />;
  }
  if (type === "ROOT_NODE" || type === "FOLDER") {
    return <FolderIcon className="shrink-0 text-[var(--yak-components-muted-text)]" />;
  }
  if (type === "BUSINESS_TYPE") {
    return <PlanningIcon name="business-type" size={14} className="text-[var(--yak-components-muted-text)]" />;
  }
  if (type === "DATA_DOMAIN") {
    return <PlanningIcon name="data-domain" size={14} className="text-[var(--yak-components-muted-text)]" />;
  }
  if (type === "BUSINESS_PROCESS") {
    return <PlanningIcon name="business-process" size={14} className="text-[var(--yak-components-muted-text)]" />;
  }
  if (type === "MART") {
    return <PlanningIcon name="data-mart" size={14} className="text-[var(--yak-components-muted-text)]" />;
  }
  if (type === "SUBJECT") {
    return <PlanningIcon name="subject-domain" size={14} className="text-[var(--yak-components-muted-text)]" />;
  }
  return <Table2 size={14} className="shrink-0 text-[var(--yak-components-muted-text)]" />;
}

type TreeRowsProps = {
  nodes: DimensionTreeNode[];
  depth: number;
  expandedKeys: Set<string>;
  selectedTreeKey: string;
  onToggle: (key: string) => void;
  onSelect: (node: DimensionTreeNode) => void;
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
    <div className="flex flex-col">
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
              aria-expanded={hasChildren ? expanded : undefined}
              onClick={() => {
                if (node.leaf) onSelect(node);
                else if (hasChildren) onToggle(node.key);
              }}
              className={[
                "group flex min-h-8 w-full items-center gap-1 rounded-none px-0 text-left text-xs leading-6 transition-colors",
                selected
                  ? "bg-[#DFE7FF] text-[var(--yak-components-panel-text)]"
                  : "text-[var(--yak-components-panel-text)] hover:bg-[var(--yak-color-hover)]",
              ].join(" ")}
              style={{
                paddingLeft: depth * 24,
                boxShadow: "0 0 0 0.5px #eceff780",
              }}
            >
              <span className="flex size-4 shrink-0 items-center justify-center text-[var(--yak-components-muted-text)]">
                {hasChildren ? <ExpandArrowIcon expanded={expanded} /> : null}
              </span>
              <DimensionNodeIcon type={node.type} />
              <span className="min-w-0 flex-1 truncate">{node.title}</span>
              {!node.leaf && typeof node.total === "number" ? (
                <span className="shrink-0 pr-2 text-[11px] tabular-nums text-[var(--yak-components-muted-text)]">
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

function DimensionTreeSkeleton() {
  const rows = [
    { depth: 0, width: 84, count: true },
    { depth: 1, width: 112, count: false },
    { depth: 1, width: 96, count: false },
    { depth: 2, width: 72, count: false },
    { depth: 2, width: 88, count: false },
    { depth: 1, width: 120, count: false },
  ];

  return (
    <div role="status" aria-label="正在加载目录" className="flex flex-col gap-1 px-2 py-2">
      <span className="sr-only">正在加载目录...</span>
      {rows.map((row, index) => (
        <div
          key={index}
          className="flex min-h-8 items-center gap-1"
          style={{ paddingLeft: row.depth * 24 }}
        >
          <Skeleton width={8} height={8} className="rounded-[2px]" />
          <Skeleton width={14} height={14} className="rounded-sm" />
          <Skeleton width={row.width} height={10} />
          {row.count ? <Skeleton width={20} height={9} className="ml-auto mr-2" /> : null}
        </div>
      ))}
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
  emptyText,
  onRefresh,
  onCreate,
  onList,
  onToggle,
  onSelect,
}: DimensionSidebarProps) {
  const [scopeKeyword, setScopeKeyword] = useState("");
  const scopeTypeOptions = dwType === "SOURCE_LAYER"
    ? SOURCE_SCOPE_OPTIONS
    : dwType === "APPLICATION_LAYER"
      ? APPLICATION_SCOPE_OPTIONS
      : COMMON_SCOPE_OPTIONS;
  const activeScope = scopeTypeOptions.find((option) => option.value === groupBy)
    || scopeTypeOptions[0]!;

  const scopeOptionsQuery = useQuery<ModelingRecord[] | BusinessTypeTreeNode[]>({
    queryKey: [
      "modeling",
      "dimension-tree-scope-options",
      projectCode,
      dwType,
      groupBy,
    ],
    queryFn: () => {
      switch (groupBy) {
        case "BUSINESS_TYPE": return businessTypeApi.tree(projectCode);
        case "DATA_MART": return dataMartApi.all<ModelingRecord>(projectCode);
        case "SUBJECT_DOMAIN": return subjectDomainApi.all<ModelingRecord>(projectCode);
        default: return dataDomainApi.all<ModelingRecord>(projectCode);
      }
    },
    enabled: Boolean(projectCode),
  });

  const isBusinessTypeTree = groupBy === "BUSINESS_TYPE";
  const businessTypeTreeOptions = useMemo(() => {
    if (!isBusinessTypeTree) return [];
    return ((scopeOptionsQuery.data || []) as BusinessTypeTreeNode[])
      .map(mapBusinessTypeTreeOption)
      .filter((option): option is TreeSelectOption => option !== null);
  }, [isBusinessTypeTree, scopeOptionsQuery.data]);

  const scopeOptions = useMemo<ScopeOption[]>(() => {
    if (isBusinessTypeTree) return [];
    const records = (scopeOptionsQuery.data || []) as ModelingRecord[];
    const fields = groupBy === "DATA_DOMAIN"
      ? ["cnName", "domainName", "enName"]
      : groupBy === "DATA_MART"
        ? ["martName", "cnName", "enName"]
        : ["domainName", "cnName", "enName"];
    return records.map((record) => ({
      value: String(record.id || ""),
      label: recordLabel(record, fields),
    })).filter((option) => option.value);
  }, [groupBy, isBusinessTypeTree, scopeOptionsQuery.data]);

  const selectedBusinessTypeOption = findBusinessTypeTreeOption(
    businessTypeTreeOptions,
    scopeValue,
  );
  const selectedScopeOption = scopeOptions.find((option) => option.value === scopeValue);
  const selectedScopeValue = isBusinessTypeTree
    ? selectedBusinessTypeOption?.value || ""
    : selectedScopeOption?.value || "";

  useEffect(() => {
    setScopeKeyword("");
  }, [groupBy]);

  useEffect(() => {
    if (!scopeValue || !scopeOptionsQuery.isSuccess) return;
    if (!selectedScopeValue) onScopeValueChange("");
  }, [
    onScopeValueChange,
    scopeOptionsQuery.isSuccess,
    scopeValue,
    selectedScopeValue,
  ]);

  const scopeSelectPlaceholder = scopeOptionsQuery.isError
    ? "加载失败，请刷新重试"
    : scopeOptionsQuery.isPending && !scopeOptionsQuery.data
      ? "正在加载..."
      : "请选择" + activeScope.label;

  const visibleScopeOptions = scopeOptions.filter((option) =>
    option.label.toLocaleLowerCase().includes(scopeKeyword.trim().toLocaleLowerCase()),
  );

  const handleLayerKeyDown = (
    event: KeyboardEvent<HTMLButtonElement>,
    index: number,
  ) => {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    const direction = event.key === "ArrowRight" ? 1 : -1;
    const nextIndex = (index + direction + DIMENSION_WAREHOUSE_OPTIONS.length)
      % DIMENSION_WAREHOUSE_OPTIONS.length;
    const nextOption = DIMENSION_WAREHOUSE_OPTIONS[nextIndex];
    if (!nextOption) return;
    onDwTypeChange(nextOption.value);
    event.currentTarget.parentElement
      ?.querySelectorAll<HTMLButtonElement>('[role="radio"]')[nextIndex]
      ?.focus();
  };

  return (
    <aside className="flex w-[292px] min-w-[260px] max-w-[360px] shrink-0 flex-col border-r border-[var(--yak-components-panel-border)] bg-white">
      <div className="flex shrink-0 items-center justify-between px-3 pb-2 pt-3">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold">维度建模</span>
        </div>
        <div className="flex items-center gap-0.5">
          <DropdownMenu>
            <DropdownMenuTrigger
              aria-label="新建维度模型"
              title="新建维度模型"
              className="inline-flex size-7 cursor-pointer items-center justify-center rounded-md border-0 bg-transparent text-[var(--yak-components-panel-text)] outline-none transition-colors hover:bg-[var(--yak-color-hover)] focus-visible:ring-2 focus-visible:ring-[var(--yak-color-primary)]"
            >
              <Plus size={14} />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" side="bottom" className="w-44">
              <DropdownMenuGroup>
                <DropdownMenuLabel>维度模型</DropdownMenuLabel>
                <DropdownMenuItem onClick={() => onCreate("DIM")}>
                  <ModelTypeIcon type="DIM" size={14} />
                  创建维度
                </DropdownMenuItem>
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuGroup>
                <DropdownMenuLabel>逻辑模型</DropdownMenuLabel>
                {DIMENSION_CREATE_OPTIONS.filter((option) => option.group === "table").map((option) => (
                  <DropdownMenuItem
                    key={option.type}
                    onClick={() => onCreate(option.type)}
                  >
                    <ModelTypeIcon type={option.type} size={14} />
                    {option.label}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuGroup>
            </DropdownMenuContent>
          </DropdownMenu>
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title="模型列表" onClick={onList}>
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
          aria-label="数仓分类"
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
                onKeyDown={(event) => handleLayerKeyDown(event, index)}
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

        <div className="flex h-6 w-full min-w-0">
          {dwType === "SOURCE_LAYER" ? (
            <span
              title="业务分类"
              aria-label="贴源层仅按业务分类筛选"
              className="flex h-6 w-[60px] min-w-[60px] shrink-0 items-center justify-center rounded-l-[4px] border border-r-0 border-[var(--yak-components-select-border)] bg-white"
            >
              <ScopeIcon scope="BUSINESS_TYPE" />
            </span>
          ) : (
            <Select
              size="small"
              value={activeScope.value}
              onValueChange={(value) => onGroupByChange(
                String(value || activeScope.value) as DimensionTreeGrouping,
              )}
            >
              <SelectTrigger
                aria-label="选择目录类型"
                variant="outlined"
                className="!h-6 !w-[60px] !min-w-[60px] !shrink-0 !rounded-r-none !px-2 [&>span:last-child>svg]:!size-3"
              >
                <SelectValue className="!inline-flex !h-6 !items-center !justify-center !leading-none">
                  {() => <ScopeIcon scope={activeScope.value} />}
                </SelectValue>
              </SelectTrigger>
              <SelectContent className="!min-w-[148px]">
                {scopeTypeOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value} className="!min-h-7 !px-2">
                    <ScopeIcon scope={option.value} />
                    <SelectItemText>{option.label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}

          {isBusinessTypeTree ? (
            <TreeSelect
              ariaLabel="选择业务分类"
              items={businessTypeTreeOptions}
              value={selectedScopeValue || null}
              placeholder={scopeSelectPlaceholder}
              searchPlaceholder="搜索业务分类"
              emptyText={scopeOptionsQuery.isError ? "加载失败，请刷新重试" : "暂无业务分类"}
              disabled={scopeOptionsQuery.isPending && !scopeOptionsQuery.data}
              size="small"
              variant="outlined"
              className="!w-0 !min-w-0 !flex-1 [&>button]:!h-6 [&>button]:!min-h-6 [&>button]:!rounded-l-none [&>button]:!rounded-r-[4px] [&>button]:!border-l-0 [&>button]:!px-2 [&>button]:!gap-1 [&>button]:!text-[11px] [&>button>span:last-child>svg]:!size-3"
              popupClassName="!min-w-[240px]"
              onValueChange={(value) => onScopeValueChange(value || "")}
            />
          ) : (
            <Select
              size="small"
              value={selectedScopeValue || undefined}
              onValueChange={(value) => onScopeValueChange(String(value || ""))}
            >
              <SelectTrigger
                aria-label={"选择" + activeScope.label}
                variant="outlined"
                className="!h-6 !min-w-0 !flex-1 !rounded-l-none !border-l-0 !px-2 [&>span:last-child>svg]:!size-3"
              >
                <SelectValue placeholder={scopeSelectPlaceholder}>
                  {(value) => scopeOptions.find((option) => option.value === String(value ?? ""))?.label || scopeSelectPlaceholder}
                </SelectValue>
              </SelectTrigger>
              <SelectContent
                header={
                  <SelectSearch
                    size="small"
                    value={scopeKeyword}
                    placeholder={"搜索" + activeScope.label}
                    onChange={(event) => setScopeKeyword(event.target.value)}
                  />
                }
                emptyContent={
                  scopeOptionsQuery.isError
                    ? "加载失败，请刷新重试"
                    : visibleScopeOptions.length === 0
                      ? "没有匹配的" + activeScope.label
                      : undefined
                }
              >
                {visibleScopeOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value} className="!min-h-7 !px-2">
                    <SelectItemText>{option.label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>

        <div className="relative">
          <Search size={13} className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
          <Input
            size="small"
            variant="outlined"
            value={keyword}
            placeholder="搜索维度或维度表"
            className="!h-6 !rounded-[4px] !pl-8 !pr-2 text-[11px]"
            onChange={(event) => onKeywordChange(event.target.value)}
          />
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto pb-3">
        {initialLoading ? (
          <DimensionTreeSkeleton />
        ) : (
          <>
            {error ? (
              <div className="flex items-center gap-1.5 px-2 py-2 text-xs text-[var(--yak-components-muted-text)]">
                <span>{root ? "目录刷新失败，当前展示上次数据。" : "目录加载失败。"}</span>
                <Button size="sm" variant="secondary" onClick={onRefresh}>重试</Button>
              </div>
            ) : null}
            {root ? (
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
                  <div className="px-2 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
                    {emptyText}
                  </div>
                ) : null}
              </>
            ) : !error ? (
              <div className="px-2 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
                {emptyText}
              </div>
            ) : null}
          </>
        )}
      </div>
    </aside>
  );
}

export default DimensionSidebar;
