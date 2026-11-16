import { IndicatorSidebar } from "../workbench/components/sidebar/indicator-sidebar";
import { useTimePeriodIndicatorTree } from "./hooks/use-time-period-indicator-tree";
import type { IndicatorPageProps } from "../workbench/types";
import { TIME_PERIOD_DEFINITION } from "./config";
import { IndicatorTabContent, type IndicatorTabContentProps } from "../workbench/components/workspace/indicator-tab-content";

export function TimePeriodPage(props: IndicatorPageProps) {
  const tree = useTimePeriodIndicatorTree(props.projectCode, props.active);

  return (
    <div aria-hidden={!props.active} className={props.active ? "flex h-full min-h-0" : "hidden"}>
      <IndicatorSidebar
        projectCode={props.projectCode}
        activeKind="timePeriod"
        definition={TIME_PERIOD_DEFINITION}
        dwType={props.dwType}
        onDwTypeChange={props.onDwTypeChange}
        modifierScopeType="DATA_DOMAIN"
        onModifierScopeTypeChange={() => {}}
        modifierScopeValue=""
        onModifierScopeValueChange={() => {}}
        groupBy="BUSINESS_TYPE"
        onGroupByChange={() => {}}
        treeKeyword={tree.treeKeyword}
        onTreeKeywordChange={tree.setTreeKeyword}
        visibleTree={tree.visibleTree}
        expandedKeys={tree.visibleExpandedKeys}
        selectedTreeKey={tree.selectedTreeKey}
        loading={tree.treeQuery.isPending || tree.treeQuery.isFetching}
        error={tree.treeQuery.isError}
        onRefresh={() => void tree.treeQuery.refetch()}
        onCreate={props.onCreate}
        onList={props.onList}
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
