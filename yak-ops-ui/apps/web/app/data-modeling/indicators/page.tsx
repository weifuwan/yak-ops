import { useParams } from "react-router-dom";
import { useWorkspace } from "@/hooks/use-workspace";

import { DataIndicatorsWorkbench } from "./workbench";
import type { IndicatorKind } from "./workbench";

const INDICATOR_ROUTE_KINDS: Record<string, IndicatorKind> = {
  atom: "atomIndicator",
  derivative: "derivativeIndicator",
  compose: "composeIndicator",
  modifier: "modifier",
  "time-period": "timePeriod",
};

export function DataModelingIndicatorsPage() {
  const { indicatorKind = "compose" } = useParams();
  const { currentWorkspace } = useWorkspace();
  const activeKind = INDICATOR_ROUTE_KINDS[indicatorKind] ?? "composeIndicator";

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
      activeKind={activeKind}
    />
  );
}

export default DataModelingIndicatorsPage;
