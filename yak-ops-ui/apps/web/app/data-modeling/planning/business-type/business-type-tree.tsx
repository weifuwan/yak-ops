import { useEffect, useRef, useState, type MouseEvent as ReactMouseEvent } from "react";
import {
  ChevronDown, ChevronRight, Pencil, Plus, PlusSquare, RefreshCw, Search, Trash2, X,
} from "lucide-react";
import { Button, Input } from "@yak-ops/yak-ui";

import type { BusinessTypeTreeNode } from "@/service/data-modeling/planning/business-type";
import { PlanningIcon } from "../components/icons";

interface Props {
  width: number;
  treeData: BusinessTypeTreeNode[];
  selectedId: string;
  expandedKeys: Set<string>;
  keyword: string;
  loading: boolean;
  error: boolean;
  refreshing: boolean;
  onKeywordChange: (keyword: string) => void;
  onToggleExpanded: (key: string) => void;
  onRefresh: () => void;
  onRetry: () => void;
  onCreateRoot: () => void;
  onSelect: (node: BusinessTypeTreeNode) => void;
  onCreateChild: (node: BusinessTypeTreeNode) => void;
  onEdit: (node: BusinessTypeTreeNode) => void;
  onDelete: (node: BusinessTypeTreeNode) => void;
}

function TreeNodeRow({
  node, depth, selectedId, expandedKeys, onToggle, onSelect, onContextMenu,
}: {
  node: BusinessTypeTreeNode; depth: number; selectedId: string; expandedKeys: Set<string>;
  onToggle: (key: string) => void; onSelect: (node: BusinessTypeTreeNode) => void;
  onContextMenu: (event: ReactMouseEvent, node: BusinessTypeTreeNode) => void;
}) {
  const key = node.key || node.value;
  const hasChildren = Boolean(node.children?.length);
  const expanded = expandedKeys.has(key);
  const selected = selectedId === key;
  return (
    <div>
      <div
        role="treeitem" aria-selected={selected} aria-expanded={hasChildren ? expanded : undefined}
        onContextMenu={(event) => onContextMenu(event, node)}
        onClick={() => onSelect(node)}
        style={{ paddingLeft: 10 + depth * 18 }}
        className={[
          "flex min-h-8 cursor-pointer items-center gap-1 rounded-md pr-2 text-[13px] transition-colors",
          selected ? "bg-[var(--yak-color-primary-subtle)] font-medium text-[var(--yak-color-primary)]" : "text-[#3a3d51] hover:bg-[var(--yak-color-hover)]",
        ].join(" ")}
      >
        <button
          type="button" className="flex size-4 shrink-0 cursor-pointer items-center justify-center text-[#98a0aa]"
          aria-label={hasChildren ? (expanded ? "收起" : "展开") : "无子级"}
          onClick={(event) => { event.stopPropagation(); if (hasChildren) onToggle(key); }}
        >
          {hasChildren ? (expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />) : null}
        </button>
        <PlanningIcon name="business-type" size={14} className={selected ? "text-[var(--yak-color-primary)]" : "text-[#8b929e]"} />
        <span className="min-w-0 flex-1 truncate">{node.title}</span>
      </div>
      {hasChildren && expanded ? (
        <div role="group">
          {node.children?.map((child) => (
            <TreeNodeRow key={child.key || child.value} node={child} depth={depth + 1}
              selectedId={selectedId} expandedKeys={expandedKeys} onToggle={onToggle}
              onSelect={onSelect} onContextMenu={onContextMenu} />
          ))}
        </div>
      ) : null}
    </div>
  );
}

export function BusinessTypeTree({
  width, treeData, selectedId, expandedKeys, keyword, loading, error, refreshing,
  onKeywordChange, onToggleExpanded, onRefresh, onRetry, onCreateRoot, onSelect,
  onCreateChild, onEdit, onDelete,
}: Props) {
  const treeRef = useRef<HTMLElement | null>(null);
  const [contextMenu, setContextMenu] = useState<{
    x: number;
    y: number;
    node: BusinessTypeTreeNode;
  }>();

  useEffect(() => {
    if (!contextMenu) return;
    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!treeRef.current?.contains(event.target as Node)) setContextMenu(undefined);
    };
    window.addEventListener("pointerdown", closeOnOutsideClick);
    return () => window.removeEventListener("pointerdown", closeOnOutsideClick);
  }, [contextMenu]);

  const runContextAction = (action: "add" | "edit" | "delete") => {
    const node = contextMenu?.node;
    if (!node) return;
    setContextMenu(undefined);
    if (action === "add") onCreateChild(node);
    if (action === "edit") onEdit(node);
    if (action === "delete") onDelete(node);
  };

  return (
    <aside
      ref={treeRef}
      className="relative flex min-h-0 shrink-0 flex-col overflow-hidden bg-white"
      style={{ width }}
      onClick={() => contextMenu && setContextMenu(undefined)}
    >
      <div className="flex h-11 shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] px-3">
        <span className="truncate text-sm font-semibold text-[var(--yak-components-panel-text)]">业务分类</span>
        <div className="flex items-center gap-1">
          <button type="button" title="新建一级业务分类"
            className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]"
            onClick={onCreateRoot}>
            <PlusSquare size={16} />
          </button>
          <button type="button" title="刷新"
            className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]"
            onClick={onRefresh}>
            <RefreshCw size={15} className={refreshing ? "animate-spin" : ""} />
          </button>
        </div>
      </div>
      <div className="shrink-0 px-3 py-2">
        <div className="relative">
          <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
          <Input size="small" variant="outlined" value={keyword} className="pl-8 pr-8"
            placeholder="搜索" onValueChange={(value) => onKeywordChange(String(value))} />
          {keyword ? (
            <button type="button" aria-label="清空搜索"
              className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer text-[var(--yak-components-muted-text)]"
              onClick={() => onKeywordChange("")}>
              <X size={13} />
            </button>
          ) : null}
        </div>
      </div>
      <div role="tree" className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
        {loading ? (
          <div className="py-8 text-center text-xs text-[var(--yak-components-muted-text)]">正在加载业务分类...</div>
        ) : error ? (
          <div className="px-2 py-6 text-center text-xs text-[var(--yak-components-muted-text)]">
            <p>业务分类加载失败</p>
            <Button size="sm" variant="secondary" className="mt-2" onClick={onRetry}>重试</Button>
          </div>
        ) : treeData.length === 0 ? (
          <div className="px-2 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
            {keyword ? "没有找到匹配的业务分类" : "暂无业务分类，点击右上角新建"}
          </div>
        ) : treeData.map((node) => (
          <TreeNodeRow key={node.key || node.value} node={node} depth={0} selectedId={selectedId}
            expandedKeys={expandedKeys} onToggle={onToggleExpanded} onSelect={onSelect}
            onContextMenu={(event, item) => {
              event.preventDefault();
              event.stopPropagation();
              setContextMenu({ x: event.clientX, y: event.clientY, node: item });
            }} />
        ))}
      </div>
      {contextMenu ? (
        <div className="fixed z-[70] min-w-40 rounded-lg border border-[var(--yak-components-panel-border)] bg-white p-1 shadow-[var(--yak-components-panel-shadow)]"
          style={{ left: contextMenu.x, top: contextMenu.y }}
          onClick={(event) => event.stopPropagation()}>
          <button type="button"
            className="flex h-8 w-full cursor-pointer items-center gap-2 rounded px-2.5 text-left text-xs hover:bg-[var(--yak-color-hover)]"
            onClick={() => runContextAction("add")}><Plus size={13} />新增子业务分类</button>
          <button type="button"
            className="flex h-8 w-full cursor-pointer items-center gap-2 rounded px-2.5 text-left text-xs hover:bg-[var(--yak-color-hover)]"
            onClick={() => runContextAction("edit")}><Pencil size={13} />编辑</button>
          <button type="button"
            className="flex h-8 w-full cursor-pointer items-center gap-2 rounded px-2.5 text-left text-xs text-[#d92d20] hover:bg-[var(--yak-color-hover)]"
            onClick={() => runContextAction("delete")}><Trash2 size={13} />删除</button>
        </div>
      ) : null}
    </aside>
  );
}

export default BusinessTypeTree;
