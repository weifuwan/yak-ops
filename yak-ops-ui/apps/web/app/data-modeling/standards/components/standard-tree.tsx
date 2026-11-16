import { useEffect, useRef, useState, type MouseEvent as ReactMouseEvent } from "react";
import { FileText, Layers, Pencil, Plus, RefreshCw, Search, Trash2, X } from "lucide-react";
import { Button, Input, Skeleton } from "@yak-ops/yak-ui";

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

function StandardTreeSkeleton() {
  const rows = [
    { depth: 0, width: 92 },
    { depth: 1, width: 116 },
    { depth: 1, width: 84 },
    { depth: 2, width: 76 },
    { depth: 2, width: 104 },
    { depth: 1, width: 112 },
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
        </div>
      ))}
    </div>
  );
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
        className={[
          "group flex min-h-8 w-full items-center gap-1 rounded-none px-0 text-left text-xs leading-6 transition-colors",
          selected
            ? "bg-[#DFE7FF] text-[var(--yak-components-panel-text)]"
            : "text-[var(--yak-components-panel-text)] hover:bg-[var(--yak-color-hover)]",
        ].join(" ")}
        style={{ paddingLeft: depth * 24, boxShadow: "0 0 0 0.5px #eceff780" }}
      >
        <button
          type="button"
          className="flex size-4 shrink-0 cursor-pointer items-center justify-center text-[var(--yak-components-muted-text)]"
          aria-label={hasChildren ? (expanded ? "收起" : "展开") : "无子级"}
          onClick={(event) => { event.stopPropagation(); if (hasChildren) onToggle(node.key); }}
        >
          {hasChildren ? <ExpandArrowIcon expanded={expanded} /> : null}
        </button>
        {node.type === "FOLDER" || node.type === "STANDARD"
          ? <FolderIcon className="shrink-0 text-[var(--yak-components-muted-text)]" />
          : node.type === "STANDARD_SET"
            ? <Layers size={14} className="shrink-0 text-[var(--yak-components-muted-text)]" />
            : <FileText size={14} className="shrink-0 text-[var(--yak-components-muted-text)]" />}
        <span className="min-w-0 flex-1 truncate">{node.title}</span>
        {node.type === "STANDARD_SET" ? (
          <span className="shrink-0 text-[10px] text-[var(--yak-components-muted-text)]">标准集</span>
        ) : null}
        {!directory ? (
          <span className="shrink-0 text-[10px] text-[var(--yak-components-muted-text)]">
            {node.type === "FIELD_STANDARD" ? "字段" : node.type === "CODE_STANDARD" ? "代码" : node.type === "UNIT_STANDARD" ? "单位" : ""}
          </span>
        ) : null}
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
      <div className="flex shrink-0 items-center justify-between px-3 pb-2 pt-3">
        <span className="truncate text-sm font-semibold text-[var(--yak-components-panel-text)]">{title}</span>
        <div className="flex items-center gap-0.5">
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title={"新建" + title + "目录"} onClick={onCreateRoot}>
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
          <Input size="small" variant="outlined" value={keyword} className="pl-8 pr-8"
            placeholder="搜索" onValueChange={(value) => onKeywordChange(String(value))} />
          {keyword ? <button type="button" aria-label="清空搜索"
            className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer text-[var(--yak-components-muted-text)]"
            onClick={() => onKeywordChange("")}><X size={13} /></button> : null}
        </div>
      </div>
      <div role="tree" className="min-h-0 flex-1 overflow-y-auto pb-3">
        {loading ? (
          <StandardTreeSkeleton />
        ) : error ? (
          <div className="flex items-center gap-1.5 px-2 py-2 text-xs text-[var(--yak-components-muted-text)]">
            <span>{treeRows.length ? "目录刷新失败，当前展示上次数据。" : "目录加载失败。"}</span>
            <Button size="sm" variant="secondary" onClick={onRetry}>重试</Button>
          </div>
        ) : treeRows.length ? (
          <div className="flex flex-col">
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
          <div className="px-2 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
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
