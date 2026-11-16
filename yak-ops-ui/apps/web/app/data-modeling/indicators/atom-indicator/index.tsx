import { Segmented, Select, SelectContent, SelectItem, SelectItemIndicator, SelectItemText, SelectTrigger, SelectValue } from "@yak-ops/yak-ui";

import { IndicatorSidebar } from "../workbench/components/sidebar/indicator-sidebar";
import { IndicatorTreeSearch } from "../workbench/components/sidebar/indicator-tree-search";
import { DW_TYPE_OPTIONS } from "../workbench/config/indicator-options";
import { selectOptionLabel } from "../workbench/utils/indicator-form";
import { ATOM_INDICATOR_DEFINITION } from "./config";
import { IndicatorTabContent, type IndicatorTabContentProps } from "../workbench/components/workspace/indicator-tab-content";
import { useAtomIndicatorTree } from "./hooks/use-atom-indicator-tree";
import type { IndicatorPageProps } from "../workbench/types";

export function AtomIndicatorPage(props: IndicatorPageProps) {
  const tree = useAtomIndicatorTree(props.projectCode, props.dwType, props.active);
  const treeControls = (
    <>
      <div className="px-3 pb-2"><div className="flex items-center gap-2">
        <span className="shrink-0 text-[11px] text-[var(--yak-components-muted-text)]">数仓分层</span>
        <Select size="small" value={props.dwType} onValueChange={(value) => props.onDwTypeChange(String(value || "PUBLIC_LAYER"))}>
          <SelectTrigger variant="outlined" className="min-w-0 flex-1">
            <SelectValue placeholder="请选择数仓分层">
              {(value) => selectOptionLabel(DW_TYPE_OPTIONS, value, "请选择数仓分层")}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>{DW_TYPE_OPTIONS.map((option) => <SelectItem key={option.value} value={option.value}><SelectItemText>{option.label}</SelectItemText><SelectItemIndicator /></SelectItem>)}</SelectContent>
        </Select>
      </div></div>
      <div className="px-3 pb-2">
        <Segmented className="w-full" value={tree.groupBy} options={[
          { value: "BUSINESS_TYPE", label: "业务分类" },
          { value: "DATA_DOMAIN", label: "数据域" },
        ]} onValueChange={(value) => tree.setGroupBy(value as typeof tree.groupBy)} />
      </div>
      <IndicatorTreeSearch value={tree.treeKeyword} placeholder="搜索业务分类、数据域、业务过程或指标" onChange={tree.setTreeKeyword} />
    </>
  );

  return (
    <div aria-hidden={!props.active} className={props.active ? "flex h-full min-h-0" : "hidden"}>
      <IndicatorSidebar
        emptyText={tree.treeKeyword ? "没有匹配的指标" : "暂无指标，点击上方 + 新建"}
        activeKind="atomIndicator" definition={ATOM_INDICATOR_DEFINITION} treeControls={treeControls}
        visibleTree={tree.visibleTree} expandedKeys={tree.visibleExpandedKeys} selectedTreeKey={tree.selectedTreeKey}
        loading={tree.treeQuery.isFetching} initialLoading={tree.treeQuery.isPending && tree.treeQuery.isFetching} error={tree.treeQuery.isError}
        onRefresh={() => void tree.treeQuery.refetch()} onCreate={props.onCreate} onList={props.onList}
        onToggle={(key) => {
          tree.setSelectedTreeKey(key);
          tree.setExpandedKeys((current) => {
            const next = new Set(current);
            if (next.has(key)) next.delete(key);
            else next.add(key);
            return next;
          });
        }}
        onSelect={(record) => {
          tree.setSelectedTreeKey("asset:" + String(record.id || ""));
          props.onSelect(record);
        }}
        onContextMenu={props.onContextMenu}
      />
    </div>
  );
}

export default AtomIndicatorPage;

export function AtomIndicatorTabContent(props: IndicatorTabContentProps) {
  return <IndicatorTabContent {...props} definition={ATOM_INDICATOR_DEFINITION} />;
}
