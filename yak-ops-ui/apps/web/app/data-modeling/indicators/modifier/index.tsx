import { IndicatorSidebar } from "../workbench/components/sidebar/indicator-sidebar";
import { IndicatorTreeFilter } from "../workbench/components/sidebar/indicator-tree-filter";
import { MODIFIER_TREE_DW_OPTIONS } from "../workbench/config/indicator-options";
import { IndicatorTabContent, type IndicatorTabContentProps } from "../workbench/components/workspace/indicator-tab-content";
import { ModifierEditorFields } from "./form/modifier-editor-fields";
import { validateModifierSubmit } from "./form/validation";
import { useModifierIndicatorTree } from "./hooks/use-modifier-indicator-tree";
import type { IndicatorEditorFieldsProps, IndicatorPageProps } from "../workbench/types";
import { MODIFIER_DEFINITION } from "./config";

export function ModifierPage(props: IndicatorPageProps) {
  const treeDwType = MODIFIER_TREE_DW_OPTIONS.some((option) => option.value === props.dwType)
    ? props.dwType
    : MODIFIER_TREE_DW_OPTIONS[0].value;
  const tree = useModifierIndicatorTree(props.projectCode, treeDwType, props.active);
  const treeControls = (
    <IndicatorTreeFilter
      projectCode={props.projectCode}
      dwType={treeDwType}
      dwTypeOptions={MODIFIER_TREE_DW_OPTIONS}
      onDwTypeChange={props.onDwTypeChange}
      scopeType={tree.modifierScopeType}
      onScopeTypeChange={tree.setModifierScopeType}
      scopeValue={tree.modifierScopeValue}
      onScopeValueChange={tree.setModifierScopeValue}
      keyword={tree.treeKeyword}
      onKeywordChange={tree.setTreeKeyword}
    />
  );

  return (
    <div aria-hidden={!props.active} className={props.active ? "flex h-full min-h-0" : "hidden"}>
      <IndicatorSidebar
        emptyText={tree.treeKeyword ? "没有匹配的指标" : "暂无指标，点击上方 + 新建"}
        activeKind="modifier" definition={MODIFIER_DEFINITION} treeControls={treeControls}
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

export default ModifierPage;

const renderModifierFields = (props: IndicatorEditorFieldsProps) => (
  <ModifierEditorFields {...props} />
);

export function ModifierIndicatorTabContent(props: IndicatorTabContentProps) {
  return (
    <IndicatorTabContent
      {...props}
      definition={MODIFIER_DEFINITION}
      renderFields={renderModifierFields}
      validateBeforeSubmit={validateModifierSubmit}
    />
  );
}
