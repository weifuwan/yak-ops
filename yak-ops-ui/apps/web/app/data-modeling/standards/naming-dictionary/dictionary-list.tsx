import { ChevronLeft, ChevronRight, FileText, Plus, RefreshCw, Search, X } from "lucide-react";
import { Button, Input, Skeleton } from "@yak-ops/yak-ui";

import { DICTIONARY_PAGE_SIZE, type DictionaryStandardRecord } from "./types";

interface Props {
  width: number; items: DictionaryStandardRecord[]; total: number;
  pageNo: number; pageCount: number; keyword: string; selectedId: string;
  loading: boolean; error: boolean; refreshing: boolean;
  onCreate: () => void; onRefresh: () => void; onRetry: () => void;
  onKeywordChange: (value: string) => void; onSelect: (id: string) => void;
  onPageChange: (page: number) => void;
}

function DictionaryListSkeleton() {
  const widths = [116, 88, 132, 104, 96, 124];

  return (
    <div role="status" aria-label="正在加载命名词典" className="flex flex-col gap-1 px-2 py-2">
      <span className="sr-only">正在加载命名词典...</span>
      {widths.map((width, index) => (
        <div key={index} className="flex min-h-8 items-center gap-1">
          <Skeleton width={8} height={8} className="rounded-[2px]" />
          <Skeleton width={14} height={14} className="rounded-sm" />
          <Skeleton width={width} height={10} />
        </div>
      ))}
    </div>
  );
}

export function DictionaryListPanel({
  width, items, total, pageNo, pageCount, keyword, selectedId, loading, error, refreshing,
  onCreate, onRefresh, onRetry, onKeywordChange, onSelect, onPageChange,
}: Props) {
  return (
    <aside className="flex min-h-0 shrink-0 flex-col overflow-hidden bg-white" style={{ width }}>
      <div className="flex shrink-0 items-center justify-between px-3 pb-2 pt-3">
        <span className="truncate text-sm font-semibold text-[var(--yak-components-panel-text)]">命名词典</span>
        <div className="flex items-center gap-0.5">
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title="新建命名词典" onClick={onCreate}>
            <Plus size={14} />
          </Button>
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title="刷新" isDisabled={refreshing} onClick={onRefresh}>
            <RefreshCw size={13} className={refreshing ? "animate-spin" : ""} />
          </Button>
        </div>
      </div>
      <div className="shrink-0 px-3 pb-2">
        <div className="relative">
          <Search size={13} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
          <Input size="small" variant="outlined" value={keyword} className="pl-8 pr-8" placeholder="搜索"
            onValueChange={(value) => onKeywordChange(String(value))} />
          {keyword ? <button type="button" aria-label="清空搜索"
            className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer text-[var(--yak-components-muted-text)]"
            onClick={() => onKeywordChange("")}><X size={13} /></button> : null}
        </div>
      </div>
      <div role="tree" className="min-h-0 flex-1 overflow-y-auto pb-3">
        {loading ? (
          <DictionaryListSkeleton />
        ) : error ? (
          <div className="flex items-center gap-1.5 px-2 py-2 text-xs text-[var(--yak-components-muted-text)]">
            <span>{items.length ? "命名词典刷新失败，当前展示上次数据。" : "命名词典加载失败。"}</span>
            <Button size="sm" variant="secondary" onClick={onRetry}>重试</Button>
          </div>
        ) : items.length ? (
          <div className="flex flex-col">
            {items.map((item) => {
              const selected = selectedId === item.id;
              return (
                <div
                  key={item.id}
                  role="treeitem"
                  aria-selected={selected}
                  onClick={() => item.id && onSelect(item.id)}
                  className={[
                    "group flex min-h-8 w-full cursor-pointer items-center gap-1 rounded-none px-0 text-left text-xs leading-6 transition-colors",
                    selected
                      ? "bg-[#DFE7FF] text-[var(--yak-components-panel-text)]"
                      : "text-[var(--yak-components-panel-text)] hover:bg-[var(--yak-color-hover)]",
                  ].join(" ")}
                  style={{ boxShadow: "0 0 0 0.5px #eceff780" }}
                >
                  <span className="flex size-4 shrink-0 items-center justify-center" />
                  <FileText size={14} className="shrink-0 text-[var(--yak-components-muted-text)]" />
                  <span className="min-w-0 flex-1 truncate">{item.cnName || item.enShortName || item.enName || "未命名词条"}</span>
                  {item.flag === 1 ? (
                    <span className="shrink-0 rounded bg-[var(--yak-color-hover)] px-1.5 py-0.5 text-[10px] text-[var(--yak-components-muted-text)]">系统</span>
                  ) : null}
                </div>
              );
            })}
          </div>
        ) : (
          <div className="px-2 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
            {keyword ? "没有匹配的命名词典" : "暂无命名词典，点击上方按钮新建"}
          </div>
        )}
      </div>
      {total > 0 ? (
        <div className="flex h-10 shrink-0 items-center justify-between border-t border-[var(--yak-components-panel-border)] px-3">
          <span className="text-[11px] text-[var(--yak-components-muted-text)]">共 {total} 项</span>
          {total > DICTIONARY_PAGE_SIZE ? (
            <div className="flex items-center gap-1">
              <button type="button" aria-label="上一页" disabled={pageNo <= 1 || refreshing}
                className="flex size-6 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] disabled:cursor-not-allowed disabled:opacity-40"
                onClick={() => onPageChange(Math.max(1, pageNo - 1))}><ChevronLeft size={14} /></button>
              <span className="text-[11px] tabular-nums text-[var(--yak-components-muted-text)]">{pageNo}/{pageCount}</span>
              <button type="button" aria-label="下一页" disabled={pageNo >= pageCount || refreshing}
                className="flex size-6 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] disabled:cursor-not-allowed disabled:opacity-40"
                onClick={() => onPageChange(Math.min(pageCount, pageNo + 1))}><ChevronRight size={14} /></button>
            </div>
          ) : null}
        </div>
      ) : null}
    </aside>
  );
}

export default DictionaryListPanel;
