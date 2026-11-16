import { Outlet, useLocation } from "react-router-dom";
import { useWorkspace } from "@/hooks/use-workspace";

import { ModelingWorkspaceEmptyState } from "@/app/data-modeling/components/workspace-empty-state";
import { DataIndicatorsWorkbench } from "./workbench";
import type { IndicatorKind } from "./workbench/types";

const ROUTE_KIND: Record<string, IndicatorKind> = {
  atom: "atomIndicator",
  derivative: "derivativeIndicator",
  compose: "composeIndicator",
  modifier: "modifier",
  "time-period": "timePeriod",
};

export default function IndicatorsIndexPage() {
  const { currentWorkspace } = useWorkspace();
  const { pathname } = useLocation();

  if (!currentWorkspace) return <ModelingWorkspaceEmptyState />;

  const segment = pathname.slice(pathname.lastIndexOf("/") + 1);
  const activeKind = ROUTE_KIND[segment] || "composeIndicator";

  return (
    <div className="h-full min-h-0">
      <DataIndicatorsWorkbench
        projectCode={String(currentWorkspace.id)}
        activeKind={activeKind}
      />
      <Outlet />
    </div>
  );
}
