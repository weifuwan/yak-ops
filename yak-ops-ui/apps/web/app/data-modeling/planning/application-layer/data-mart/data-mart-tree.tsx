import { useEffect, useRef, useState, type MouseEvent as ReactMouseEvent } from "react";
import { Pencil, Plus, RefreshCw, Search, Trash2, X } from "lucide-react";
import { Button, Input } from "@yak-ops/yak-ui";

import type { DataMartTreeNode } from "./types";
import { PlanningTreeRow, PlanningTreeSkeleton } from "../../components/tree-visuals";

interface Props {
  width: number;
  treeData: DataMartTreeNode[];
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
  onSelect: (node: DataMartTreeNode) => void;
  onCreateChild: (node: DataMartTreeNode) => void;
  onEdit: (node: DataMartTreeNode) => void;
  onDelete: (node: DataMartTreeNode) => void;
}

export function getExpandableKeys(nodes: DataMartTreeNode[]): string[] {
  return nodes.reduce<string[]>((keys, node) => {
    if (node.children?.length) keys.push(node.value || node.key, ...getExpandableKeys(node.children));
    return keys;
  }, []);
}

function TreeNodeRow({
  node, depth, selectedId, expandedKeys, onToggle, onSelect, onContextMenu,
}: {
  node: DataMartTreeNode; depth: number; selectedId: string; expandedKeys: Set<string>;
  onToggle: (key: string) => void; onSelect: (node: DataMartTreeNode) => void;
  onContextMenu: (event: ReactMouseEvent, node: DataMartTreeNode) => void;
}) {
  const key = node.value || node.key;
  const hasChildren = Boolean(node.children?.length);
  const expanded = expandedKeys.has(key);
  const selected = selectedId === key;

  return (
    <div>
      <PlanningTreeRow
        title={node.title}
        icon="data-mart"
        depth={depth}
        selected={selected}
        hasChildren={hasChildren}
        expanded={expanded}
        onToggle={() => onToggle(key)}
        onSelect={() => onSelect(node)}
        onContextMenu={(event) => onContextMenu(event, node)}
      />
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

export function DataMartTree({
  width, treeData, selectedId, expandedKeys, keyword, loading, error, refreshing,
  onKeywordChange, onToggleExpanded, onRefresh, onRetry, onCreateRoot, onSelect,
  onCreateChild, onEdit, onDelete,
}: Props) {
  const treeRef = useRef<HTMLElement | null>(null);
  const [contextMenu, setContextMenu] = useState<{
    x: number;
    y: number;
    node: DataMartTreeNode;
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
    <aside ref={treeRef}
      className="relative flex min-h-0 shrink-0 flex-col overflow-hidden bg-white"
      style={{ width }}
      onClick={() => contextMenu && setContextMenu(undefined)}>
      <div className="flex shrink-0 items-center justify-between px-3 pb-2 pt-3">
        <span className="truncate text-sm font-semibold text-[var(--yak-components-panel-text)]">数据集市</span>
        <div className="flex items-center gap-0.5">
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0"
            title="新建一级数据集市" onClick={onCreateRoot}><Plus size={14} /></Button>
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0"
            title="刷新" isDisabled={refreshing} onClick={onRefresh}>
            <RefreshCw size={13} className={refreshing ? "animate-spin" : ""} />
          </Button>
        </div>
      </div>
      <div className="shrink-0 px-3 pb-2">
        <div className="relative">
          <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
          <Input size="small" variant="outlined" value={keyword} className="pl-8 pr-8"
            placeholder="搜索" onValueChange={(value) => onKeywordChange(String(value))} />
          {keyword ? (
            <button type="button" aria-label="清空搜索"
              className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer text-[var(--yak-components-muted-text)]"
              onClick={() => onKeywordChange("")}><X size={13} /></button>
          ) : null}
        </div>
      </div>
      <div role="tree" className="min-h-0 flex-1 overflow-y-auto pb-3">
        {loading ? (
          <PlanningTreeSkeleton />
        ) : error ? (
          <div className="flex items-center gap-1.5 px-2 py-2 text-xs text-[var(--yak-components-muted-text)]">
            <span>数据集市加载失败。</span>
            <Button size="sm" variant="secondary" onClick={onRetry}>重试</Button>
          </div>
        ) : treeData.length ? (
          <div className="flex flex-col">
            {treeData.map((node) => (
              <TreeNodeRow key={node.key || node.value} node={node} depth={0}
                selectedId={selectedId} expandedKeys={expandedKeys} onToggle={onToggleExpanded}
                onSelect={onSelect}
                onContextMenu={(event, item) => {
                  event.preventDefault();
                  event.stopPropagation();
                  setContextMenu({ x: event.clientX, y: event.clientY, node: item });
                }} />
            ))}
          </div>
        ) : (
          <div className="px-2 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
            {keyword ? "没有找到匹配的数据集市" : "暂无数据集市，点击右上角新建"}
          </div>
        )}
      </div>
      {contextMenu ? (
        <div className="fixed z-[100] min-w-[148px] rounded-md border border-[#e4e7ec] bg-white p-1 shadow-[0_8px_24px_rgba(16,24,40,0.14)]"
          style={{ left: contextMenu.x, top: contextMenu.y }}
          onClick={(event) => event.stopPropagation()}>
          <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
            onClick={() => runContextAction("add")}><Plus size={13} />新增子数据集市</button>
          <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
            onClick={() => runContextAction("edit")}><Pencil size={13} />编辑</button>
          <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#d92d20] hover:bg-[#fef3f2]"
            onClick={() => runContextAction("delete")}><Trash2 size={13} />删除</button>
        </div>
      ) : null}
    </aside>
  );
}

export default DataMartTree;
