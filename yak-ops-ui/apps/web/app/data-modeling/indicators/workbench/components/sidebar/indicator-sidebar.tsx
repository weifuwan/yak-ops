import {
  Button, Input, Segmented, Select, SelectContent, SelectItem, SelectItemIndicator,
  SelectItemText, SelectTrigger, SelectValue,
} from "@yak-ops/yak-ui";
import { CalendarDays, ChartNoAxesCombined, Folder, List, Plus, RefreshCw, Search } from "lucide-react";

import type { ModelingRecord } from "../../../../service";
import { DW_TYPE_OPTIONS } from "../../config/indicator-definitions";
import type { IndicatorDefinition, IndicatorGroupBy, IndicatorKind, IndicatorTreeNode } from "../../types";
import { IndicatorTreeRows } from "./indicator-tree";

export type IndicatorSidebarProps = {
  activeKind: IndicatorKind;
  definition: IndicatorDefinition;
  dwType: string;
  onDwTypeChange: (value: string) => void;
  groupBy: IndicatorGroupBy;
  onGroupByChange: (value: IndicatorGroupBy) => void;
  treeKeyword: string;
  onTreeKeywordChange: (value: string) => void;
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
  activeKind, definition, dwType, onDwTypeChange, groupBy, onGroupByChange,
  treeKeyword, onTreeKeywordChange, visibleTree, expandedKeys, selectedTreeKey,
  loading, error, onRefresh, onCreate, onList, onToggle, onSelect, onContextMenu,
}: IndicatorSidebarProps) {
  return (
    <aside className="flex w-[292px] min-w-[260px] max-w-[360px] shrink-0 flex-col border-r border-[var(--yak-components-panel-border)] bg-white">
      <div className="flex shrink-0 items-center justify-between px-3 pb-2 pt-3">
        <div className="flex items-center gap-2">
          {activeKind === "timePeriod" ? <CalendarDays size={15} className="text-[var(--yak-components-muted-text)]" /> : activeKind === "atomIndicator" ? <ChartNoAxesCombined size={15} className="text-[var(--yak-components-muted-text)]" /> : <Folder size={15} className="text-[var(--yak-components-muted-text)]" />}
          <span className="text-sm font-semibold">{definition.title}</span>
        </div>
        <div className="flex items-center gap-0.5">
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title={"新建" + definition.title} onClick={onCreate}><Plus size={14} /></Button>
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title="列表模式" onClick={onList}><List size={14} /></Button>
          <Button size="sm" variant="ghost" className="!h-7 !w-7 !p-0" title="刷新" isDisabled={loading} onClick={onRefresh}><RefreshCw size={13} className={loading ? "animate-spin" : ""} /></Button>
        </div>
      </div>
      {activeKind === "atomIndicator" ? (
        <div className="px-3 pb-2"><div className="flex items-center gap-2">
          <span className="shrink-0 text-[11px] text-[var(--yak-components-muted-text)]">数仓分层</span>
          <Select size="small" value={dwType} onValueChange={(value) => onDwTypeChange(String(value || "PUBLIC_LAYER"))}>
            <SelectTrigger variant="outlined" className="min-w-0 flex-1"><SelectValue placeholder="请选择数仓分层" /></SelectTrigger>
            <SelectContent>{DW_TYPE_OPTIONS.map((option) => <SelectItem key={option.value} value={option.value}><SelectItemText>{option.label}</SelectItemText><SelectItemIndicator /></SelectItem>)}</SelectContent>
          </Select>
        </div></div>
      ) : null}
      {activeKind !== "timePeriod" ? (
        <div className="px-3 pb-2"><Segmented className="w-full" value={groupBy} options={[{ value: "BUSINESS_TYPE", label: "业务分类" }, { value: "DATA_DOMAIN", label: "数据域" }]} onValueChange={(value) => onGroupByChange(value as IndicatorGroupBy)} /></div>
      ) : null}
      <div className="shrink-0 px-3 pb-2"><div className="relative">
        <Search size={13} className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
        <Input size="small" variant="outlined" className="pl-8" value={treeKeyword} placeholder={activeKind === "atomIndicator" ? "搜索业务分类、数据域、业务过程或指标" : "搜索目录或指标"} onChange={(event) => onTreeKeywordChange(event.target.value)} />
      </div></div>
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
        {loading ? <div className="flex items-center gap-2 px-2 py-4 text-xs text-[var(--yak-components-muted-text)]"><RefreshCw size={13} className="animate-spin" />正在加载目录...</div> : error ? (
          <div className="px-2 py-4 text-xs text-[var(--yak-components-muted-text)]">目录加载失败。<Button size="sm" variant="secondary" className="ml-2" onClick={onRefresh}>重试</Button></div>
        ) : visibleTree.length ? (
          <IndicatorTreeRows nodes={visibleTree} depth={0} expandedKeys={expandedKeys} selectedKey={selectedTreeKey} onToggle={onToggle} onSelect={onSelect} onContextMenu={onContextMenu} />
        ) : <div className="px-2 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">{treeKeyword ? "没有匹配的指标" : "暂无指标，点击上方 + 新建"}</div>}
      </div>
    </aside>
  );
}
