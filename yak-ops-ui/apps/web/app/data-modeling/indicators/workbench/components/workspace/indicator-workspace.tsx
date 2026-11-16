import type { ComponentType } from "react";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type { IndicatorKind, IndicatorTab } from "../../types";
import { AtomIndicatorTabContent } from "../../../atom-indicator";
import { DerivativeIndicatorTabContent } from "../../../derivative-indicator";
import { CompositeIndicatorTabContent } from "../../../composite-indicator";
import { ModifierIndicatorTabContent } from "../../../modifier";
import { TimePeriodIndicatorTabContent } from "../../../time-period";
import type { IndicatorTabContentProps } from "./indicator-tab-content";
import { IndicatorEmptyState } from "./indicator-empty-state";

type Props = {
  projectCode: string;
  dwType: string;
  tabs: IndicatorTab[];
  activeTabKey: string;
  onEdit: (kind: IndicatorKind, record: ModelingRecord) => void;
  onDelete: (kind: IndicatorKind, record: ModelingRecord) => void;
  onSaved: (kind: IndicatorKind, tabKey: string, record: ModelingRecord) => void;
  onDraftChange: (kind: IndicatorKind, tabKey: string, values: Record<string, string>) => void;
};

const TAB_CONTENT: Record<IndicatorKind, ComponentType<IndicatorTabContentProps>> = {
  atomIndicator: AtomIndicatorTabContent,
  derivativeIndicator: DerivativeIndicatorTabContent,
  composeIndicator: CompositeIndicatorTabContent,
  modifier: ModifierIndicatorTabContent,
  timePeriod: TimePeriodIndicatorTabContent,
};

export function IndicatorWorkspace({
  projectCode,
  dwType,
  tabs,
  activeTabKey,
  onEdit,
  onDelete,
  onSaved,
  onDraftChange,
}: Props) {
  const activeTab = tabs.find((tab) => tab.key === activeTabKey) || null;

  return (
    <div className="relative min-h-0 flex-1 overflow-hidden">
      {tabs.map((tab) => {
        const isActive = tab.key === activeTabKey;
        const TabContent = TAB_CONTENT[tab.kind];
        return (
          <div
            key={tab.key}
            aria-hidden={!isActive}
            className={["absolute inset-0 min-h-0", isActive ? "flex" : "hidden"].join(" ")}
          >
            <TabContent
              projectCode={projectCode}
              tab={tab}
              dwType={dwType}
              onEdit={onEdit}
              onDelete={onDelete}
              onSaved={onSaved}
              onDraftChange={onDraftChange}
            />
          </div>
        );
      })}
      {!activeTab ? <IndicatorEmptyState /> : null}
    </div>
  );
}
