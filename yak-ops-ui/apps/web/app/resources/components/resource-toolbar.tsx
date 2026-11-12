import {
  Grid2X2,
  List,
  RefreshCw,
  Search,
  Trash2,
  X,
} from "lucide-react";
import {
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";

import type { ResourceSort, ResourceTypeFilter, ViewMode } from "../types";

interface ResourceToolbarProps {
  keyword: string;
  onKeywordChange: (value: string) => void;
  onClearKeyword: () => void;
  selectedCount: number;
  canDeleteSelected: boolean;
  onClearSelection: () => void;
  onDeleteSelected: () => void;
  typeFilter: ResourceTypeFilter;
  onTypeFilterChange: (value: ResourceTypeFilter) => void;
  sortBy: ResourceSort;
  onSortChange: (value: ResourceSort) => void;
  viewMode: ViewMode;
  onViewModeChange: (value: ViewMode) => void;
  onRefresh: () => void;
  isLoading: boolean;
  isFetching: boolean;
}

export function ResourceToolbar({
  keyword,
  onKeywordChange,
  onClearKeyword,
  selectedCount,
  canDeleteSelected,
  onClearSelection,
  onDeleteSelected,
  typeFilter,
  onTypeFilterChange,
  sortBy,
  onSortChange,
  viewMode,
  onViewModeChange,
  onRefresh,
  isLoading,
  isFetching,
}: ResourceToolbarProps) {
  return (
    <div className="mb-4 flex min-h-9 flex-wrap items-center justify-between gap-3">
      <div className="flex min-w-0 flex-1 items-center gap-3">
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

        {selectedCount > 0 ? (
          <div className="flex items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
            <span>已选择 {selectedCount} 项</span>
            <Button
              variant="ghost"
              size="sm"
              className="h-7 px-1.5 text-xs"
              onClick={onClearSelection}
            >
              取消选择
            </Button>
            <Button
              variant="ghost"
              size="sm"
              isDisabled={!canDeleteSelected}
              onClick={onDeleteSelected}
              className="h-7 px-1.5 text-xs text-[var(--yak-color-danger)] hover:text-[var(--yak-color-danger)]"
            >
              <Trash2 size={13} />
              批量删除
            </Button>
          </div>
        ) : null}
      </div>

      <div className="flex shrink-0 items-center gap-2">
        <Select
          size="small"
          value={typeFilter}
          onValueChange={(value) => onTypeFilterChange(value as ResourceTypeFilter)}
        >
          <SelectTrigger variant="outlined" className="w-[124px]">
            <SelectValue placeholder="全部类型" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">全部类型</SelectItem>
            <SelectItem value="FOLDER">文件夹</SelectItem>
            <SelectItem value="FILE">所有文件</SelectItem>
            <SelectItem value="SQL">SQL</SelectItem>
            <SelectItem value="JAR">JAR</SelectItem>
            <SelectItem value="PYTHON">Python</SelectItem>
            <SelectItem value="SHELL">Shell</SelectItem>
            <SelectItem value="IMAGE">图片</SelectItem>
            <SelectItem value="DOCUMENT">文档与配置</SelectItem>
            <SelectItem value="OTHER">其他</SelectItem>
          </SelectContent>
        </Select>

        <Select
          size="small"
          value={sortBy}
          onValueChange={(value) => onSortChange(value as ResourceSort)}
        >
          <SelectTrigger variant="outlined" className="w-[124px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="name">按名称排序</SelectItem>
            <SelectItem value="updated">最近修改</SelectItem>
            <SelectItem value="size">文件大小</SelectItem>
          </SelectContent>
        </Select>

        <div className="flex h-8 items-center rounded-md border border-[var(--yak-components-panel-border)] bg-white p-0.5">
          <Button
            type="button"
            variant={viewMode === "list" ? "secondary" : "ghost"}
            size="sm"
            isIconOnly
            aria-label="列表视图"
            aria-pressed={viewMode === "list"}
            title="列表视图"
            onClick={() => onViewModeChange("list")}
            className="size-7"
          >
            <List size={15} />
          </Button>
          <Button
            type="button"
            variant={viewMode === "grid" ? "secondary" : "ghost"}
            size="sm"
            isIconOnly
            aria-label="网格视图"
            aria-pressed={viewMode === "grid"}
            title="网格视图"
            onClick={() => onViewModeChange("grid")}
            className="size-7"
          >
            <Grid2X2 size={14} />
          </Button>
        </div>

        <Button
          type="button"
          variant="ghost"
          size="sm"
          isIconOnly
          aria-label="刷新资源"
          title="刷新"
          onClick={onRefresh}
          isDisabled={isLoading}
          className="size-8"
        >
          <RefreshCw size={14} className={isFetching ? "animate-spin" : ""} />
        </Button>
      </div>
    </div>
  );
}
