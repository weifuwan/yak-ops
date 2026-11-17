import { IndicatorSidebar } from "../workbench/components/sidebar/indicator-sidebar";
import { IndicatorTreeFilter } from "../workbench/components/sidebar/indicator-tree-filter";
import { DERIVATIVE_INDICATOR_TREE_DW_OPTIONS } from "../workbench/config/indicator-options";
import { useGroupedIndicatorTree } from "../workbench/hooks/use-grouped-indicator-tree";
import type { IndicatorPageProps } from "../workbench/types";
import { DERIVATIVE_INDICATOR_DEFINITION } from "./config";
import { DerivativeIndicatorEditorFields } from "./form/derivative-indicator-editor-fields";
import { validateDerivativeIndicator } from "./form/validation";
import { IndicatorTabContent, type IndicatorTabContentProps } from "../workbench/components/workspace/indicator-tab-content";

export function DerivativeIndicatorPage(props: IndicatorPageProps) {
  const treeDwType = DERIVATIVE_INDICATOR_TREE_DW_OPTIONS.some((option) => option.value === props.dwType)
    ? props.dwType
    : DERIVATIVE_INDICATOR_TREE_DW_OPTIONS[0].value;
  const tree = useGroupedIndicatorTree(props.projectCode, "derivativeIndicator", treeDwType, props.active);
  const treeControls = (
    <IndicatorTreeFilter
      projectCode={props.projectCode}
      businessTypeTreeSelect
      dwType={treeDwType}
      dwTypeOptions={DERIVATIVE_INDICATOR_TREE_DW_OPTIONS}
      groupingMode="WAREHOUSE"
      onDwTypeChange={props.onDwTypeChange}
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
        activeKind="derivativeIndicator" definition={DERIVATIVE_INDICATOR_DEFINITION} treeControls={treeControls}
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

export default DerivativeIndicatorPage;

export function DerivativeIndicatorTabContent(props: IndicatorTabContentProps) {
  return (
    <IndicatorTabContent
      {...props}
      definition={DERIVATIVE_INDICATOR_DEFINITION}
      renderFields={(fieldProps) => <DerivativeIndicatorEditorFields {...fieldProps} />}
      validateBeforeSave={validateDerivativeIndicator}
      validateBeforeSubmit={validateDerivativeIndicator}
    />
  );
}
