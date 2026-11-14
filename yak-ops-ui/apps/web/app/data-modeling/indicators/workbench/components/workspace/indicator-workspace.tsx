import type { ModelingRecord } from "../../../../service";
import type { IndicatorDefinition, IndicatorKind, IndicatorTab } from "../../types";
import { IndicatorEmptyState } from "./indicator-empty-state";
import { IndicatorTabContent } from "./indicator-tab-content";

type Props = {
  projectCode: string; activeKind: IndicatorKind; definition: IndicatorDefinition; dwType: string;
  tabs: IndicatorTab[]; activeTabKey: string;
  onEdit: (kind: IndicatorKind, record: ModelingRecord) => void;
  onDelete: (kind: IndicatorKind, record: ModelingRecord) => void;
  onSaved: (kind: IndicatorKind, tabKey: string, record: ModelingRecord) => void;
  onDraftChange: (kind: IndicatorKind, tabKey: string, values: Record<string, string>) => void;
  onCreate: () => void;
};

export function IndicatorWorkspace({ projectCode, activeKind, definition, dwType, tabs, activeTabKey, onEdit, onDelete, onSaved, onDraftChange, onCreate }: Props) {
  const activeTab = tabs.find((tab) => tab.key === activeTabKey) || null;
  return <div className="relative min-h-0 flex-1 overflow-hidden">
    {tabs.map((tab) => {
      const isActive = tab.key === activeTabKey;
      return <div key={tab.key} aria-hidden={!isActive} className={["absolute inset-0 min-h-0", isActive ? "flex" : "hidden"].join(" ")}>
        <IndicatorTabContent projectCode={projectCode} tab={tab} dwType={dwType} onEdit={onEdit} onDelete={onDelete} onSaved={onSaved} onDraftChange={onDraftChange} />
      </div>;
    })}
    {!activeTab ? <IndicatorEmptyState activeKind={activeKind} definition={definition} onCreate={onCreate} /> : null}
  </div>;
}
