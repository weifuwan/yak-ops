import { IndicatorSidebar } from "../workbench/components/sidebar/indicator-sidebar";
import { IndicatorTabContent, type IndicatorTabContentProps } from "../workbench/components/workspace/indicator-tab-content";
import { ModifierEditorFields } from "./form/modifier-editor-fields";
import { validateModifierSubmit } from "./form/validation";
import { ModifierTreeFilter } from "./components/modifier-tree-filter";
import { useModifierIndicatorTree } from "./hooks/use-modifier-indicator-tree";
import type { IndicatorEditorFieldsProps, IndicatorPageProps } from "../workbench/types";
import { MODIFIER_DEFINITION } from "./config";

export function ModifierPage(props: IndicatorPageProps) {
  const tree = useModifierIndicatorTree(props.projectCode, props.dwType, props.active);
  const treeControls = (
    <ModifierTreeFilter
      projectCode={props.projectCode}
      dwType={props.dwType}
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
