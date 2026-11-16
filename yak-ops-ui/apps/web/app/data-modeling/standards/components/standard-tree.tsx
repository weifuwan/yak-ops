import { useEffect, useRef, useState, type MouseEvent as ReactMouseEvent } from "react";
import {
  ChevronDown, ChevronRight, FileText, Folder, FolderOpen, Layers, LoaderCircle,
  Pencil, Plus, PlusSquare, RefreshCw, Search, Trash2, X,
} from "lucide-react";
import { Button, Input } from "@yak-ops/yak-ui";

import type { StandardModule, StandardTreeNode } from "../types";
import { isDirectoryNode } from "../types";

interface Props {
  width: number; title: string; module: StandardModule; treeRows: StandardTreeNode[];
  selectedNode?: StandardTreeNode; expandedKeys: Set<string>; keyword: string;
  loading: boolean; error: boolean; refreshing: boolean;
  onKeywordChange: (value: string) => void; onToggleExpanded: (key: string) => void;
  onRefresh: () => void; onRetry: () => void; onCreateRoot: () => void;
  onSelect: (node: StandardTreeNode) => void;
  onCreateDirectory: (node: StandardTreeNode) => void;
  onCreateStandardSet: (node: StandardTreeNode) => void;
  onCreateRecord: (node: StandardTreeNode) => void;
  onEditDirectory: (node: StandardTreeNode) => void;
  onEditRecord: (node: StandardTreeNode) => void;
  onDeleteDirectory: (node: StandardTreeNode) => void;
  onDeleteRecord: (node: StandardTreeNode) => void;
}
interface ContextMenuState { x: number; y: number; node: StandardTreeNode; }

export function filterTree(nodes: StandardTreeNode[], keyword: string): StandardTreeNode[] {
  const normalized = keyword.trim().toLocaleLowerCase();
  if (!normalized) return nodes;
  return nodes.reduce<StandardTreeNode[]>((result, node) => {
    const children = filterTree(node.children || [], normalized);
    if (node.title.toLocaleLowerCase().includes(normalized) || children.length) {
      result.push({ ...node, children });
    }
    return result;
  }, []);
}

export function getExpandableKeys(nodes: StandardTreeNode[]): string[] {
  return nodes.reduce<string[]>((keys, node) => {
    if (node.children?.length) keys.push(node.key, ...getExpandableKeys(node.children));
    return keys;
  }, []);
}

export function findNodePath(nodes: StandardTreeNode[], key: string, path: string[] = []): string[] {
  for (const node of nodes) {
    const next = [...path, node.title];
    if (node.key === key) return next;
    const found = findNodePath(node.children || [], key, next);
    if (found.length) return found;
  }
  return [];
}

function TreeNodeRow({
  node, depth, selectedKey, expandedKeys, onToggle, onSelect, onContextMenu,
}: {
  node: StandardTreeNode; depth: number; selectedKey: string; expandedKeys: Set<string>;
  onToggle: (key: string) => void; onSelect: (node: StandardTreeNode) => void;
  onContextMenu: (event: ReactMouseEvent, node: StandardTreeNode) => void;
}) {
  const hasChildren = Boolean(node.children?.length);
  const expanded = expandedKeys.has(node.key);
  const selected = selectedKey === node.key;
  const directory = isDirectoryNode(node);
  return (
    <div>
      <div
        role="treeitem"
        aria-selected={selected}
        aria-expanded={hasChildren ? expanded : undefined}
        onContextMenu={(event) => onContextMenu(event, node)}
        onClick={() => onSelect(node)}
        style={{ paddingLeft: 10 + depth * 18 }}
        className={[
          "flex min-h-8 cursor-pointer items-center gap-1 rounded-md pr-2 text-[13px] transition-colors",
          selected
            ? "bg-[var(--yak-color-primary-subtle)] font-medium text-[var(--yak-color-primary)]"
            : "text-[#3a3d51] hover:bg-[var(--yak-color-hover)]",
        ].join(" ")}
      >
        <button
          type="button"
          className="flex size-4 shrink-0 cursor-pointer items-center justify-center text-[#98a0aa]"
          aria-label={hasChildren ? (expanded ? "收起" : "展开") : "无子级"}
          onClick={(event) => { event.stopPropagation(); if (hasChildren) onToggle(node.key); }}
        >
          {hasChildren ? (expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />) : null}
        </button>
        {node.type === "FOLDER"
          ? (expanded ? <FolderOpen size={14} className="shrink-0 text-[#8b929e]" /> : <Folder size={14} className="shrink-0 text-[#8b929e]" />)
          : node.type === "STANDARD_SET"
            ? <Layers size={14} className="shrink-0 text-[#8b929e]" />
            : <FileText size={14} className="shrink-0 text-[#8b929e]" />}
        <span className="min-w-0 flex-1 truncate">{node.title}</span>
        {node.type === "STANDARD_SET" ? <span className="text-[10px] text-[#98a0aa]">标准集</span> : null}
        {!directory ? <span className="text-[10px] text-[#98a0aa]">{node.type === "FIELD_STANDARD" ? "字段" : node.type === "CODE_STANDARD" ? "代码" : node.type === "UNIT_STANDARD" ? "单位" : ""}</span> : null}
      </div>
      {hasChildren && expanded ? (
        <div role="group">
          {node.children?.map((child) => (
            <TreeNodeRow key={child.key} node={child} depth={depth + 1}
              selectedKey={selectedKey} expandedKeys={expandedKeys} onToggle={onToggle}
              onSelect={onSelect} onContextMenu={onContextMenu} />
          ))}
        </div>
      ) : null}
    </div>
  );
}

export function DirectoryStandardsTree({
  width, title, module, treeRows, selectedNode, expandedKeys, keyword, loading, error, refreshing,
  onKeywordChange, onToggleExpanded, onRefresh, onRetry, onCreateRoot, onSelect,
  onCreateDirectory, onCreateStandardSet, onCreateRecord, onEditDirectory, onEditRecord,
  onDeleteDirectory, onDeleteRecord,
}: Props) {
  const treeRef = useRef<HTMLElement | null>(null);
  const [contextMenu, setContextMenu] = useState<ContextMenuState>();

  useEffect(() => {
    if (!contextMenu) return;
    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!treeRef.current?.contains(event.target as Node)) setContextMenu(undefined);
    };
    window.addEventListener("pointerdown", closeOnOutsideClick);
    return () => window.removeEventListener("pointerdown", closeOnOutsideClick);
  }, [contextMenu]);

  const runContextAction = (action: "add-directory" | "add-standard-set" | "add-record" | "edit" | "delete") => {
    const node = contextMenu?.node;
    if (!node) return;
    setContextMenu(undefined);
    if (action === "add-directory") onCreateDirectory(node);
    if (action === "add-standard-set") onCreateStandardSet(node);
    if (action === "add-record") onCreateRecord(node);
    if (action === "edit") {
      if (isDirectoryNode(node)) onEditDirectory(node);
      else onEditRecord(node);
    }
    if (action === "delete") {
      if (isDirectoryNode(node)) onDeleteDirectory(node);
      else onDeleteRecord(node);
    }
  };

  return (
    <aside ref={treeRef} className="relative flex min-h-0 shrink-0 flex-col overflow-hidden bg-white"
      style={{ width }} onClick={() => contextMenu && setContextMenu(undefined)}>
      <div className="flex h-11 shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] px-3">
        <span className="truncate text-sm font-semibold text-[var(--yak-components-panel-text)]">{title}</span>
        <div className="flex items-center gap-1">
          <button type="button" title={"新建" + title + "目录"}
            className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]"
            onClick={onCreateRoot}><PlusSquare size={16} /></button>
          <button type="button" title="刷新"
            className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]"
            onClick={onRefresh}><RefreshCw size={15} className={refreshing ? "animate-spin" : ""} /></button>
        </div>
      </div>
      <div className="shrink-0 px-3 py-2">
        <div className="relative">
          <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
          <Input size="small" variant="outlined" value={keyword} className="pl-8 pr-8"
            placeholder="搜索" onValueChange={(value) => onKeywordChange(String(value))} />
          {keyword ? <button type="button" aria-label="清空搜索"
            className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer text-[var(--yak-components-muted-text)]"
            onClick={() => onKeywordChange("")}><X size={13} /></button> : null}
        </div>
      </div>
      <div role="tree" className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
        {loading ? (
          <div className="flex items-center justify-center gap-2 py-8 text-xs text-[var(--yak-components-muted-text)]">
            <LoaderCircle size={14} className="animate-spin" />正在加载目录...
          </div>
        ) : error ? (
          <div className="flex flex-col items-center gap-2 py-8 text-xs text-[var(--yak-components-muted-text)]">
            <span>目录加载失败</span><Button size="sm" variant="secondary" onClick={onRetry}>重试</Button>
          </div>
        ) : treeRows.length ? (
          <div className="space-y-0.5">
            {treeRows.map((node) => (
              <TreeNodeRow key={node.key} node={node} depth={0}
                selectedKey={selectedNode?.key || ""} expandedKeys={expandedKeys}
                onToggle={onToggleExpanded} onSelect={onSelect}
                onContextMenu={(event, item) => {
                  event.preventDefault(); event.stopPropagation();
                  setContextMenu({ x: event.clientX, y: event.clientY, node: item });
                }} />
            ))}
          </div>
        ) : (
          <div className="py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
            {keyword ? "没有匹配的目录" : "暂无目录，点击上方按钮新建"}
          </div>
        )}
      </div>
      {contextMenu ? (
        <div className="fixed z-[100] min-w-[164px] rounded-md border border-[#e4e7ec] bg-white p-1 shadow-[0_8px_24px_rgba(16,24,40,0.14)]"
          style={{ left: contextMenu.x, top: contextMenu.y }} onClick={(event) => event.stopPropagation()}>
          {isDirectoryNode(contextMenu.node) ? (
            <>
              {contextMenu.node.type === "FOLDER" ? (
                <>
                  <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
                    onClick={() => runContextAction("add-directory")}><Plus size={13} />新建子目录</button>
                  {module === "FIELD" ? <button type="button"
                    className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
                    onClick={() => runContextAction("add-standard-set")}><Layers size={13} />新建标准集</button> : null}
                </>
              ) : null}
              <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
                onClick={() => runContextAction("add-record")}><Plus size={13} />新建{module === "FIELD" ? "字段标准" : module === "CODE" ? "标准代码" : "度量单位"}</button>
              <div className="my-1 border-t border-[#eef0f3]" />
              <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
                onClick={() => runContextAction("edit")}><Pencil size={13} />编辑目录</button>
              <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#d92d20] hover:bg-[#fef3f2]"
                onClick={() => runContextAction("delete")}><Trash2 size={13} />删除目录</button>
            </>
          ) : (
            <>
              <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
                onClick={() => runContextAction("edit")}><Pencil size={13} />编辑</button>
              <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#d92d20] hover:bg-[#fef3f2]"
                onClick={() => runContextAction("delete")}><Trash2 size={13} />删除</button>
            </>
          )}
        </div>
      ) : null}
    </aside>
  );
}

export default DirectoryStandardsTree;
