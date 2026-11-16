import type { ReactNode } from "react";
import { Button } from "@yak-ops/yak-ui";
import { List, Plus, RefreshCw } from "lucide-react";

import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import { IndicatorIcon, getIndicatorIconName } from "../../../icons";
import type { IndicatorDefinition, IndicatorKind, IndicatorTreeNode } from "../../types";
import { IndicatorTreeRows } from "./indicator-tree";

export type IndicatorSidebarProps = {
  activeKind: IndicatorKind;
  definition: IndicatorDefinition;
  treeControls?: ReactNode;
  visibleTree: IndicatorTreeNode[];
  expandedKeys: Set<string>;
  selectedTreeKey: string;
  loading: boolean;
  error: boolean;
  onRefresh: () => void;
  onCreate: () => void;
  onList: () => void;
  onToggle: (key: string) => void;
  onSelect: (record: ModelingRecord) => void;
  onContextMenu: (event: React.MouseEvent<HTMLButtonElement>, node: IndicatorTreeNode) => void;
};

export function IndicatorSidebar({
  activeKind, definition, treeControls, visibleTree, expandedKeys, selectedTreeKey,
  loading, error, onRefresh, onCreate, onList, onToggle, onSelect, onContextMenu,
}: IndicatorSidebarProps) {
  return (
    <aside className="flex w-[292px] min-w-[260px] max-w-[360px] shrink-0 flex-col border-r border-[var(--yak-components-panel-border)] bg-white">
      <div className="flex shrink-0 items-center justify-between px-3 pb-2 pt-3">
        <div className="flex items-center gap-2">
          <IndicatorIcon name={getIndicatorIconName(activeKind)} size={16} />
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
        {loading ? <div className="flex items-center gap-2 px-2 py-4 text-xs text-[var(--yak-components-muted-text)]"><RefreshCw size={13} className="animate-spin" />正在加载目录...</div> : error ? (
          <div className="px-2 py-4 text-xs text-[var(--yak-components-muted-text)]">目录加载失败。<Button size="sm" variant="secondary" className="ml-2" onClick={onRefresh}>重试</Button></div>
        ) : visibleTree.length ? (
          <IndicatorTreeRows activeKind={activeKind} nodes={visibleTree} depth={0} expandedKeys={expandedKeys} selectedKey={selectedTreeKey} onToggle={onToggle} onSelect={onSelect} onContextMenu={onContextMenu} />
        ) : <div className="px-2 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">暂无指标，点击上方 + 新建</div>}
      </div>
    </aside>
  );
}
