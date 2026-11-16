import { useWorkspace } from "@/hooks/use-workspace";

import { ModelingWorkspaceEmptyState } from "@/app/data-modeling/components/workspace-empty-state";
import { DataModelingBusinessTypePage } from "./business-type-content";

export default function BusinessTypePage() {
  const { currentWorkspace } = useWorkspace();

  if (!currentWorkspace) return <ModelingWorkspaceEmptyState />;

  return <DataModelingBusinessTypePage projectCode={String(currentWorkspace.id)} />;
}
