import { Segmented } from "@yak-ops/yak-ui";

import { IndicatorSidebar } from "../workbench/components/sidebar/indicator-sidebar";
import { IndicatorTreeSearch } from "../workbench/components/sidebar/indicator-tree-search";
import { useGroupedIndicatorTree } from "../workbench/hooks/use-grouped-indicator-tree";
import type { IndicatorPageProps } from "../workbench/types";
import { DERIVATIVE_INDICATOR_DEFINITION } from "./config";
import { IndicatorTabContent, type IndicatorTabContentProps } from "../workbench/components/workspace/indicator-tab-content";

export function DerivativeIndicatorPage(props: IndicatorPageProps) {
  const tree = useGroupedIndicatorTree(props.projectCode, "derivativeIndicator", props.active);
  const treeControls = (
    <>
      <div className="px-3 pb-2">
        <Segmented className="w-full" value={tree.groupBy} options={[
          { value: "BUSINESS_TYPE", label: "业务分类" },
          { value: "DATA_DOMAIN", label: "数据域" },
        ]} onValueChange={(value) => tree.setGroupBy(value as typeof tree.groupBy)} />
      </div>
      <IndicatorTreeSearch value={tree.treeKeyword} placeholder="搜索目录或指标" onChange={tree.setTreeKeyword} />
    </>
  );

  return (
    <div aria-hidden={!props.active} className={props.active ? "flex h-full min-h-0" : "hidden"}>
      <IndicatorSidebar
        activeKind="derivativeIndicator" definition={DERIVATIVE_INDICATOR_DEFINITION} treeControls={treeControls}
        visibleTree={tree.visibleTree} expandedKeys={tree.visibleExpandedKeys} selectedTreeKey={tree.selectedTreeKey}
        loading={tree.treeQuery.isPending || tree.treeQuery.isFetching} error={tree.treeQuery.isError}
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

export default DerivativeIndicatorPage;

export function DerivativeIndicatorTabContent(props: IndicatorTabContentProps) {
  return <IndicatorTabContent {...props} definition={DERIVATIVE_INDICATOR_DEFINITION} />;
}
