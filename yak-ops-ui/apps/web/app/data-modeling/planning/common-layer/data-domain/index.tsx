import { useWorkspace } from "@/hooks/use-workspace";

import { ModelingWorkspaceEmptyState } from "@/app/data-modeling/components/workspace-empty-state";
import { DataModelingDataDomainPage } from "./data-domain-content";

export default function DataDomainPage() {
  const { currentWorkspace } = useWorkspace();

  if (!currentWorkspace) return <ModelingWorkspaceEmptyState />;

  return <DataModelingDataDomainPage projectCode={String(currentWorkspace.id)} />;
}
