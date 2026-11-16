import { IndicatorSidebar } from "../workbench/components/sidebar/indicator-sidebar";
import { IndicatorTreeSearch } from "../workbench/components/sidebar/indicator-tree-search";
import { useTimePeriodIndicatorTree } from "./hooks/use-time-period-indicator-tree";
import type { IndicatorPageProps } from "../workbench/types";
import { TIME_PERIOD_DEFINITION } from "./config";
import { IndicatorTabContent, type IndicatorTabContentProps } from "../workbench/components/workspace/indicator-tab-content";

export function TimePeriodPage(props: IndicatorPageProps) {
  const tree = useTimePeriodIndicatorTree(props.projectCode, props.active);
  const treeControls = (
    <IndicatorTreeSearch value={tree.treeKeyword} placeholder="搜索周期目录或时间周期" onChange={tree.setTreeKeyword} />
  );

  return (
    <div aria-hidden={!props.active} className={props.active ? "flex h-full min-h-0" : "hidden"}>
      <IndicatorSidebar
        activeKind="timePeriod" definition={TIME_PERIOD_DEFINITION} treeControls={treeControls}
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

export default TimePeriodPage;

export function TimePeriodIndicatorTabContent(props: IndicatorTabContentProps) {
  return <IndicatorTabContent {...props} definition={TIME_PERIOD_DEFINITION} />;
}
