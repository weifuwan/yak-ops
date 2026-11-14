import type { ModelingRecord } from "../../../../service";
import type { IndicatorKind, IndicatorTab } from "../../types";
import { getDefinition } from "../../config/indicator-definitions";
import { useIndicatorResources } from "../../hooks/use-indicator-resources";
import { IndicatorEditor } from "./indicator-editor";
import { IndicatorList } from "./indicator-list";

type IndicatorTabContentProps = {
  projectCode: string;
  tab: IndicatorTab;
  dwType: string;
  onEdit: (kind: IndicatorKind, record: ModelingRecord) => void;
  onDelete: (kind: IndicatorKind, record: ModelingRecord) => void;
  onSaved: (kind: IndicatorKind, tabKey: string, record: ModelingRecord) => void;
  onDraftChange: (kind: IndicatorKind, tabKey: string, values: Record<string, string>) => void;
};

export function IndicatorTabContent({
  projectCode,
  tab,
  dwType,
  onEdit,
  onDelete,
  onSaved,
  onDraftChange,
}: IndicatorTabContentProps) {
  const definition = getDefinition(tab.kind);
  const { optionsByResource, isFetching: optionsLoading } = useIndicatorResources(projectCode, tab.kind);

  if (tab.mode === "list") {
    return (
      <IndicatorListTab
        projectCode={projectCode}
        definition={definition}
        dwType={dwType}
        optionsByResource={optionsByResource}
        onEdit={(record) => onEdit(tab.kind, record)}
        onDelete={(record) => onDelete(tab.kind, record)}
      />
    );
  }

  return (
    <IndicatorEditor
      projectCode={projectCode}
      definition={definition}
      tab={tab}
      optionsByResource={optionsByResource}
      optionsLoading={optionsLoading}
      onSaved={onSaved}
      onDraftChange={onDraftChange}
    />
  );
}
