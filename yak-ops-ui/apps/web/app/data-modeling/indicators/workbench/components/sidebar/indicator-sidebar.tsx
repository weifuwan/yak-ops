import type { ReactNode } from "react";
import { Button, Skeleton } from "@yak-ops/yak-ui";
import { List, Plus, RefreshCw } from "lucide-react";

import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import { IndicatorIcon, getIndicatorIconName } from "../../../icons";
import type { IndicatorDefinition, IndicatorKind, IndicatorTreeNode } from "../../types";
import { IndicatorTreeRows } from "./indicator-tree";


function IndicatorTreeSkeleton() {
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

export type IndicatorSidebarProps = {
  activeKind: IndicatorKind;
  definition: IndicatorDefinition;
  treeControls?: ReactNode;
  visibleTree: IndicatorTreeNode[];
  expandedKeys: Set<string>;
  selectedTreeKey: string;
  loading: boolean;
  initialLoading: boolean;
  error: boolean;
  emptyText: string;
  onRefresh: () => void;
  onCreate: () => void;
  onList: () => void;
  onToggle: (key: string) => void;
  onSelect: (record: ModelingRecord) => void;
  onContextMenu: (event: React.MouseEvent<HTMLButtonElement>, node: IndicatorTreeNode) => void;
};

export function IndicatorSidebar({
  activeKind, definition, treeControls, visibleTree, expandedKeys, selectedTreeKey,
  loading, initialLoading, error, emptyText, onRefresh, onCreate, onList, onToggle, onSelect, onContextMenu,
}: IndicatorSidebarProps) {
  return (
    <aside className="flex w-[292px] min-w-[260px] max-w-[360px] shrink-0 flex-col border-r border-[var(--yak-components-panel-border)] bg-white">
      <div className="flex shrink-0 items-center justify-between px-3 pb-2 pt-3">
        <div className="flex items-center gap-2">
          {activeKind !== "modifier" ? <IndicatorIcon name={getIndicatorIconName(activeKind)} size={16} /> : null}
          <span className="text-sm font-semibold">{definition.title}</span>
        </div>
        <div className="flex items-center gap-0.5">
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title={"新建" + definition.title} onClick={onCreate}><Plus size={14} /></Button>
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title="列表模式" onClick={onList}><List size={14} /></Button>
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title="刷新" isDisabled={loading} onClick={onRefresh}><RefreshCw size={13} className={loading ? "animate-spin" : ""} /></Button>
        </div>
      </div>
      {treeControls ? <div className="shrink-0">{treeControls}</div> : null}
      <div className="min-h-0 flex-1 overflow-y-auto pb-3">
        {initialLoading ? (
          <IndicatorTreeSkeleton />
        ) : (
          <>
            {error ? (
              <div className="flex items-center gap-1.5 px-2 py-2 text-xs text-[var(--yak-components-muted-text)]">
                <span>{visibleTree.length ? "目录刷新失败，当前展示上次数据。" : "目录加载失败。"}</span>
                <Button size="sm" variant="secondary" onClick={onRefresh}>重试</Button>
              </div>
            ) : null}
            {visibleTree.length ? (
              <IndicatorTreeRows activeKind={activeKind} nodes={visibleTree} depth={0} expandedKeys={expandedKeys} selectedKey={selectedTreeKey} onToggle={onToggle} onSelect={onSelect} onContextMenu={onContextMenu} />
            ) : !error ? (
              <div className="px-2 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">{emptyText}</div>
            ) : null}
          </>
        )}
      </div>
    </aside>
  );
}
