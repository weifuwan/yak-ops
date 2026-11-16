import type { ReactNode } from "react";

import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type {
  IndicatorDefinition,
  IndicatorEditorFieldsProps,
  IndicatorKind,
  IndicatorTab,
} from "../../types";
import { useIndicatorResources } from "../../hooks/use-indicator-resources";
import { IndicatorEditor } from "./indicator-editor";
import { IndicatorList } from "./indicator-list";

export type IndicatorTabContentProps = {
  projectCode: string;
  tab: IndicatorTab;
  dwType: string;
  onEdit: (kind: IndicatorKind, record: ModelingRecord) => void;
  onDelete: (kind: IndicatorKind, record: ModelingRecord) => void;
  onSaved: (kind: IndicatorKind, tabKey: string, record: ModelingRecord) => void;
  onDraftChange: (kind: IndicatorKind, tabKey: string, values: Record<string, string>) => void;
};

type Props = IndicatorTabContentProps & {
  definition: IndicatorDefinition;
  renderFields?: (props: IndicatorEditorFieldsProps) => ReactNode;
  validateBeforeSave?: (values: Record<string, string>) => string | undefined;
  validateBeforeSubmit?: (values: Record<string, string>) => string | undefined;
};

export function IndicatorTabContent({
  projectCode,
  tab,
  dwType,
  definition,
  renderFields,
  validateBeforeSave,
  validateBeforeSubmit,
  onEdit,
  onDelete,
  onSaved,
  onDraftChange,
}: Props) {
  const { optionsByResource, isFetching: optionsLoading } = useIndicatorResources(projectCode, definition);

  if (tab.mode === "list") {
    return (
      <IndicatorList
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
      renderFields={renderFields}
      validateBeforeSave={validateBeforeSave}
      validateBeforeSubmit={validateBeforeSubmit}
      onSaved={onSaved}
      onDraftChange={onDraftChange}
    />
  );
}
