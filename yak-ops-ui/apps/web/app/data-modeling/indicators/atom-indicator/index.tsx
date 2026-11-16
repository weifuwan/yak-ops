import { IndicatorSidebar } from "../workbench/components/sidebar/indicator-sidebar";
import { ATOM_INDICATOR_DEFINITION } from "./config";
import { useAtomIndicatorTree } from "./hooks/use-atom-indicator-tree";
import type { IndicatorPageProps } from "../workbench/types";

export function AtomIndicatorPage(props: IndicatorPageProps) {
  const tree = useAtomIndicatorTree(props.projectCode, props.dwType, props.active);

  return (
    <div aria-hidden={!props.active} className={props.active ? "flex h-full min-h-0" : "hidden"}>
      <IndicatorSidebar
        projectCode={props.projectCode}
        activeKind="atomIndicator"
        definition={ATOM_INDICATOR_DEFINITION}
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

export default AtomIndicatorPage;
