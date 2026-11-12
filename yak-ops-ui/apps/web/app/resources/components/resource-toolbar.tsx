import {
  Grid2X2,
  List,
  PanelRightClose,
  PanelRightOpen,
  RefreshCw,
  Search,
  X,
} from "lucide-react";
import {
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Segmented,
} from "@yak-ops/yak-ui";

import type { ResourceSort, ResourceTypeFilter, ViewMode } from "../types";

interface ResourceToolbarProps {
  keyword: string;
  onKeywordChange: (value: string) => void;
  onClearKeyword: () => void;
  typeFilter: ResourceTypeFilter;
  onTypeFilterChange: (value: ResourceTypeFilter) => void;
  sortBy: ResourceSort;
  onSortChange: (value: ResourceSort) => void;
  viewMode: ViewMode;
  onViewModeChange: (value: ViewMode) => void;
  detailsOpen: boolean;
  onToggleDetails: () => void;
  onRefresh: () => void;
  isLoading: boolean;
  isFetching: boolean;
}

export function ResourceToolbar({
  keyword,
  onKeywordChange,
  onClearKeyword,
  typeFilter,
  onTypeFilterChange,
  sortBy,
  onSortChange,
  viewMode,
  onViewModeChange,
  detailsOpen,
  onToggleDetails,
  onRefresh,
  isLoading,
  isFetching,
}: ResourceToolbarProps) {
  return (
    <div className="mb-3 flex min-h-9 flex-wrap items-center justify-between gap-3">
      <div className="relative w-[280px] max-w-full min-w-[180px]">
        <Search
          size={15}
          className="pointer-events-none absolute left-3 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
        />
        <Input
          size="small"
          variant="outlined"
          aria-label="搜索资源"
          placeholder="搜索文件或文件夹"
          value={keyword}
          onChange={(event) => onKeywordChange(event.target.value)}
          className="h-9 pl-9 pr-9 text-xs"
        />
        {keyword ? (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            isIconOnly
            aria-label="清除搜索"
            title="清除搜索"
            onClick={onClearKeyword}
            className="absolute right-1 top-1/2 size-7 -translate-y-1/2"
          >
            <X size={13} />
          </Button>
        ) : null}
      </div>

      <div className="flex shrink-0 items-center gap-2">
        <Select
          size="small"
          items={[
            { value: "ALL", label: "全部类型" },
            { value: "FOLDER", label: "文件夹" },
            { value: "FILE", label: "所有文件" },
            { value: "SQL", label: "SQL" },
            { value: "JAR", label: "JAR" },
            { value: "PYTHON", label: "Python" },
            { value: "SHELL", label: "Shell" },
            { value: "IMAGE", label: "图片" },
            { value: "DOCUMENT", label: "文档与配置" },
            { value: "OTHER", label: "其他" },
          ]}
          value={typeFilter}
          onValueChange={(value) => onTypeFilterChange(value as ResourceTypeFilter)}
        >
          <SelectTrigger variant="outlined" className="w-[124px]">
            <SelectValue placeholder="全部类型" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">
              <SelectItemText>全部类型</SelectItemText>
            </SelectItem>
            <SelectItem value="FOLDER">
              <SelectItemText>文件夹</SelectItemText>
            </SelectItem>
            <SelectItem value="FILE">
              <SelectItemText>所有文件</SelectItemText>
            </SelectItem>
            <SelectItem value="SQL">
              <SelectItemText>SQL</SelectItemText>
            </SelectItem>
            <SelectItem value="JAR">
              <SelectItemText>JAR</SelectItemText>
            </SelectItem>
            <SelectItem value="PYTHON">
              <SelectItemText>Python</SelectItemText>
            </SelectItem>
            <SelectItem value="SHELL">
              <SelectItemText>Shell</SelectItemText>
            </SelectItem>
            <SelectItem value="IMAGE">
              <SelectItemText>图片</SelectItemText>
            </SelectItem>
            <SelectItem value="DOCUMENT">
              <SelectItemText>文档与配置</SelectItemText>
            </SelectItem>
            <SelectItem value="OTHER">
              <SelectItemText>其他</SelectItemText>
            </SelectItem>
          </SelectContent>
        </Select>

        <Select
          size="small"
          items={[
            { value: "name", label: "按名称排序" },
            { value: "updated", label: "最近修改" },
            { value: "size", label: "文件大小" },
          ]}
          value={sortBy}
          onValueChange={(value) => onSortChange(value as ResourceSort)}
        >
          <SelectTrigger variant="outlined" className="w-[124px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="name">
              <SelectItemText>按名称排序</SelectItemText>
            </SelectItem>
            <SelectItem value="updated">
              <SelectItemText>最近修改</SelectItemText>
            </SelectItem>
            <SelectItem value="size">
              <SelectItemText>文件大小</SelectItemText>
            </SelectItem>
          </SelectContent>
        </Select>

        <Segmented
          aria-label="资源视图"
          value={viewMode}
          onValueChange={onViewModeChange}
          options={[
            {
              value: "list",
              label: (
                <span className="flex items-center justify-center">
                  <span className="sr-only">列表视图</span>
                  <List size={14} className="block shrink-0" aria-hidden="true" />
                </span>
              ),
            },
            {
              value: "grid",
              label: (
                <span className="flex items-center justify-center">
                  <span className="sr-only">网格视图</span>
                  <Grid2X2 size={14} className="block shrink-0" aria-hidden="true" />
                </span>
              ),
            },
          ]}
          className="h-8 items-center rounded-md p-0.5 [&>button]:inline-flex [&>button]:size-7 [&>button]:shrink-0 [&>button]:items-center [&>button]:justify-center [&>button]:px-0 [&>button]:py-0"
        />

        <Button
          type="button"
          variant={detailsOpen ? "secondary" : "ghost"}
          size="sm"
          isIconOnly
          aria-label={detailsOpen ? "关闭详情面板" : "打开详情面板"}
          aria-pressed={detailsOpen}
          title={detailsOpen ? "隐藏详情面板" : "显示详情面板"}
          onClick={onToggleDetails}
          className="size-7 shrink-0"
        >
          {detailsOpen ? (
            <PanelRightClose size={14} strokeWidth={1.8} />
          ) : (
            <PanelRightOpen size={14} strokeWidth={1.8} />
          )}
        </Button>

        <Button
          type="button"
          variant="ghost"
          size="sm"
          isIconOnly
          aria-label="刷新资源"
          title="刷新"
          onClick={onRefresh}
          isDisabled={isLoading}
          className="size-7 shrink-0"
        >
          <RefreshCw size={14} className={isFetching ? "animate-spin" : ""} />
        </Button>
      </div>
    </div>
  );
}
