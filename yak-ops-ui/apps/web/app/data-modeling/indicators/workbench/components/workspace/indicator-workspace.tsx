import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type { IndicatorKind, IndicatorTab } from "../../types";
import { IndicatorEmptyState } from "./indicator-empty-state";
import { INDICATOR_PAGE_REGISTRY } from "../../config/indicator-page-registry";

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
        const TabContent = INDICATOR_PAGE_REGISTRY[tab.kind].TabContent;
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
