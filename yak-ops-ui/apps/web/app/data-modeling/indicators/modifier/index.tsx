import { IndicatorSidebar } from "../workbench/components/sidebar/indicator-sidebar";
import { IndicatorTabContent, type IndicatorTabContentProps } from "../workbench/components/workspace/indicator-tab-content";
import { ModifierEditorFields } from "../workbench/components/workspace/modifier-editor-fields";
import type { IndicatorEditorFieldsProps } from "../workbench/types";
import { useModifierIndicatorTree } from "./hooks/use-modifier-indicator-tree";
import type { IndicatorPageProps } from "../workbench/types";
import { MODIFIER_DEFINITION } from "./config";

export function ModifierPage(props: IndicatorPageProps) {
  const tree = useModifierIndicatorTree(props.projectCode, props.dwType, props.active);

  return (
    <div aria-hidden={!props.active} className={props.active ? "flex h-full min-h-0" : "hidden"}>
      <IndicatorSidebar
        projectCode={props.projectCode}
        activeKind="modifier"
        definition={MODIFIER_DEFINITION}
        dwType={props.dwType}
        onDwTypeChange={props.onDwTypeChange}
        modifierScopeType={tree.modifierScopeType}
        onModifierScopeTypeChange={tree.setModifierScopeType}
        modifierScopeValue={tree.modifierScopeValue}
        onModifierScopeValueChange={tree.setModifierScopeValue}
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

export default ModifierPage;

const renderModifierFields = (props: IndicatorEditorFieldsProps) => (
  <ModifierEditorFields {...props} />
);

const validateModifierSubmit = (values: Record<string, string>) => {
  if (values.modifierType !== "DIMENSION") return undefined;
  const requiredFields = [
    ["dimensionTbId", "关联维度表"],
    ["dimensionFdId", "关联维度字段"],
    ["codeId", "关联标准代码"],
    ["codeDetailId", "关联代码明细"],
  ] as const;
  const missing = requiredFields.find(([key]) => !String(values[key] || "").trim());
  return missing ? "维度枚举修饰词提交前，请完成" + missing[1] : undefined;
};

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
