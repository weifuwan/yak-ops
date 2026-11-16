import { useWorkspace } from "@/hooks/use-workspace";

import { ModelingWorkspaceEmptyState } from "@/app/data-modeling/components/workspace-empty-state";
import { DataModelingBusinessProcessPage } from "./business-process-content";

export default function BusinessProcessPage() {
  const { currentWorkspace } = useWorkspace();

  if (!currentWorkspace) return <ModelingWorkspaceEmptyState />;

  return <DataModelingBusinessProcessPage projectCode={String(currentWorkspace.id)} />;
}
