import {
  Grid2X2,
  List,
  PanelRight,
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
          variant={detailsOpen ? "secondary" : "ghost"}
          size="sm"
          isIconOnly
          aria-label={detailsOpen ? "关闭详情面板" : "打开详情面板"}
          aria-pressed={detailsOpen}
          title={detailsOpen ? "关闭详情" : "显示详情"}
          onClick={onToggleDetails}
          className="size-8"
        >
          <PanelRight size={14} />
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
          className="size-8"
        >
          <RefreshCw size={14} className={isFetching ? "animate-spin" : ""} />
        </Button>
      </div>
    </div>
  );
}
