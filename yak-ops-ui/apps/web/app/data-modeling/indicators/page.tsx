import { useParams } from "react-router-dom";
import { useWorkspace } from "@/hooks/use-workspace";

import { DataIndicatorsWorkbench } from "./workbench";

const INDICATOR_ROUTE_TABS: Record<string, number> = {
  atom: 0,
  derivative: 1,
  compose: 2,
  modifier: 3,
  "time-period": 5,
};

export function DataModelingIndicatorsPage() {
  const { indicatorKind = "compose" } = useParams();
  const { currentWorkspace } = useWorkspace();
  const initialTab = INDICATOR_ROUTE_TABS[indicatorKind] ?? 2;

  if (!currentWorkspace) {
    return (
      <div className="flex min-h-[240px] h-full items-center justify-center rounded-md border border-dashed border-[var(--yak-components-panel-border)] bg-white text-sm text-[var(--yak-components-muted-text)]">
        请先选择工作空间
      </div>
    );
  }

  return (
    <DataIndicatorsWorkbench
      projectCode={String(currentWorkspace.id)}
      initialTab={initialTab}
    />
  );
}

export default DataModelingIndicatorsPage;
