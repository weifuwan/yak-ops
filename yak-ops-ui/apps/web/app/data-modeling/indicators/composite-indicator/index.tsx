import { IndicatorSidebar } from "../workbench/components/sidebar/indicator-sidebar";
import { useGroupedIndicatorTree } from "../workbench/hooks/use-grouped-indicator-tree";
import type { IndicatorPageProps } from "../workbench/types";
import { COMPOSITE_INDICATOR_DEFINITION } from "./config";

export function CompositeIndicatorPage(props: IndicatorPageProps) {
  const tree = useGroupedIndicatorTree(props.projectCode, "composeIndicator", props.active);

  return (
    <div aria-hidden={!props.active} className={props.active ? "flex h-full min-h-0" : "hidden"}>
      <IndicatorSidebar
        projectCode={props.projectCode}
        activeKind="composeIndicator"
        definition={COMPOSITE_INDICATOR_DEFINITION}
        dwType={props.dwType}
        onDwTypeChange={props.onDwTypeChange}
        modifierScopeType="DATA_DOMAIN"
        onModifierScopeTypeChange={() => {}}
        modifierScopeValue=""
        onModifierScopeValueChange={() => {}}
        groupBy={tree.groupBy}
        onGroupByChange={tree.setGroupBy}
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

export default CompositeIndicatorPage;
