import { ChevronLeft, ChevronRight, FileText, LoaderCircle, PlusSquare, RefreshCw, Search, X } from "lucide-react";
import { Button, Input } from "@yak-ops/yak-ui";

import { DICTIONARY_PAGE_SIZE, type DictionaryStandardRecord } from "./types";

interface Props {
  width: number; items: DictionaryStandardRecord[]; total: number;
  pageNo: number; pageCount: number; keyword: string; selectedId: string;
  loading: boolean; error: boolean; refreshing: boolean;
  onCreate: () => void; onRefresh: () => void; onRetry: () => void;
  onKeywordChange: (value: string) => void; onSelect: (id: string) => void;
  onPageChange: (page: number) => void;
}

export function DictionaryListPanel({
  width, items, total, pageNo, pageCount, keyword, selectedId, loading, error, refreshing,
  onCreate, onRefresh, onRetry, onKeywordChange, onSelect, onPageChange,
}: Props) {
  return (
    <aside className="flex min-h-0 shrink-0 flex-col overflow-hidden bg-white" style={{ width }}>
      <div className="flex h-11 shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] px-3">
        <span className="truncate text-sm font-semibold text-[var(--yak-components-panel-text)]">命名词典</span>
        <div className="flex items-center gap-1">
          <button type="button" title="新建命名词典"
            className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]"
            onClick={onCreate}><PlusSquare size={16} /></button>
          <button type="button" title="刷新"
            className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]"
            onClick={onRefresh}><RefreshCw size={15} className={refreshing ? "animate-spin" : ""} /></button>
        </div>
      </div>
      <div className="shrink-0 px-3 py-2">
        <div className="relative">
          <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
          <Input size="small" variant="outlined" value={keyword} className="pl-8 pr-8" placeholder="搜索"
            onValueChange={(value) => onKeywordChange(String(value))} />
          {keyword ? <button type="button" aria-label="清空搜索"
            className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer text-[var(--yak-components-muted-text)]"
            onClick={() => onKeywordChange("")}><X size={13} /></button> : null}
        </div>
      </div>
      <div role="tree" className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
        {loading ? (
          <div className="flex items-center justify-center gap-2 py-8 text-xs text-[var(--yak-components-muted-text)]">
            <LoaderCircle size={14} className="animate-spin" />正在加载命名词典...
          </div>
        ) : error ? (
          <div className="flex flex-col items-center gap-2 py-8 text-xs text-[var(--yak-components-muted-text)]">
            <span>命名词典加载失败</span><Button size="sm" variant="secondary" onClick={onRetry}>重试</Button>
          </div>
        ) : items.length ? (
          <div className="space-y-0.5">
            {items.map((item) => {
              const selected = selectedId === item.id;
              return (
                <div key={item.id} role="treeitem" aria-selected={selected}
                  onClick={() => item.id && onSelect(item.id)}
                  className={[
                    "flex min-h-8 cursor-pointer items-center gap-2 rounded-md px-2.5 pr-2 text-[13px] transition-colors",
                    selected
                      ? "bg-[var(--yak-color-primary-subtle)] font-medium text-[var(--yak-color-primary)]"
                      : "text-[#3a3d51] hover:bg-[var(--yak-color-hover)]",
                  ].join(" ")}>
                  <FileText size={14} className="shrink-0 text-[#8b929e]" />
                  <span className="min-w-0 flex-1 truncate">{item.cnName || item.enShortName || item.enName || "未命名词条"}</span>
                  {item.flag === 1 ? <span className="shrink-0 rounded bg-[#f2f4f7] px-1.5 py-0.5 text-[10px] text-[#667085]">系统</span> : null}
                </div>
              );
            })}
          </div>
        ) : (
          <div className="py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
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
