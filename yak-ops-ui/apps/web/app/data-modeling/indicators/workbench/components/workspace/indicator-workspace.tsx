import type { ModelingRecord } from "../../../../service";
import type { IndicatorTab } from "../../types";
import { IndicatorEmptyState } from "./indicator-empty-state";
import { IndicatorTabContent } from "./indicator-tab-content";

type Props = {
  projectCode: string; dwType: string;
  tabs: IndicatorTab[]; activeTabKey: string;
  onEdit: (kind: import("../../types").IndicatorKind, record: ModelingRecord) => void;
  onDelete: (kind: import("../../types").IndicatorKind, record: ModelingRecord) => void;
  onSaved: (kind: import("../../types").IndicatorKind, tabKey: string, record: ModelingRecord) => void;
  onDraftChange: (kind: import("../../types").IndicatorKind, tabKey: string, values: Record<string, string>) => void;
};

export function IndicatorWorkspace({ projectCode, dwType, tabs, activeTabKey, onEdit, onDelete, onSaved, onDraftChange }: Props) {
  const activeTab = tabs.find((tab) => tab.key === activeTabKey) || null;
  return <div className="relative min-h-0 flex-1 overflow-hidden">
    {tabs.map((tab) => {
      const isActive = tab.key === activeTabKey;
      return <div key={tab.key} aria-hidden={!isActive} className={["absolute inset-0 min-h-0", isActive ? "flex" : "hidden"].join(" ")}>
        <IndicatorTabContent projectCode={projectCode} tab={tab} dwType={dwType} onEdit={onEdit} onDelete={onDelete} onSaved={onSaved} onDraftChange={onDraftChange} />
      </div>;
    })}
    {!activeTab ? <IndicatorEmptyState /> : null}
  </div>;
}
