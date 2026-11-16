import { IndicatorSidebar } from "../workbench/components/sidebar/indicator-sidebar";
import { IndicatorTreeFilter } from "../workbench/components/sidebar/indicator-tree-filter";
import { COMPOSITE_INDICATOR_TREE_DW_OPTIONS } from "../workbench/config/indicator-options";
import { useGroupedIndicatorTree } from "../workbench/hooks/use-grouped-indicator-tree";
import type { IndicatorPageProps } from "../workbench/types";
import { COMPOSITE_INDICATOR_DEFINITION } from "./config";
import { IndicatorTabContent, type IndicatorTabContentProps } from "../workbench/components/workspace/indicator-tab-content";
import { CompositeIndicatorEditorFields } from "./form/composite-indicator-editor-fields";
import { validateCompositeIndicator } from "./form/validation";

export function CompositeIndicatorPage(props: IndicatorPageProps) {
  const treeDwType = COMPOSITE_INDICATOR_TREE_DW_OPTIONS.some((option) => option.value === props.dwType)
    ? props.dwType
    : COMPOSITE_INDICATOR_TREE_DW_OPTIONS[0].value;
  const tree = useGroupedIndicatorTree(props.projectCode, "composeIndicator", treeDwType, props.active);
  const treeControls = (
    <IndicatorTreeFilter
      projectCode={props.projectCode}
      dwType={treeDwType}
      dwTypeOptions={COMPOSITE_INDICATOR_TREE_DW_OPTIONS}
      onDwTypeChange={props.onDwTypeChange}
      groupingMode="WAREHOUSE"
      scopeType={tree.groupBy}
      onScopeTypeChange={(value) => {
        tree.setGroupBy(value);
        tree.setScopeValue("");
      }}
      scopeValue={tree.scopeValue}
      onScopeValueChange={tree.setScopeValue}
      keyword={tree.treeKeyword}
      onKeywordChange={tree.setTreeKeyword}
    />
  );

  return (
    <div aria-hidden={!props.active} className={props.active ? "flex h-full min-h-0" : "hidden"}>
      <IndicatorSidebar
        emptyText={tree.treeKeyword ? "没有匹配的指标" : "暂无指标，点击上方 + 新建"}
        activeKind="composeIndicator" definition={COMPOSITE_INDICATOR_DEFINITION} treeControls={treeControls}
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

export default CompositeIndicatorPage;

export function CompositeIndicatorTabContent(props: IndicatorTabContentProps) {
  return (
    <IndicatorTabContent
      {...props}
      definition={COMPOSITE_INDICATOR_DEFINITION}
      renderFields={(fieldProps) => <CompositeIndicatorEditorFields {...fieldProps} />}
      validateBeforeSave={validateCompositeIndicator}
      validateBeforeSubmit={validateCompositeIndicator}
    />
  );
}
