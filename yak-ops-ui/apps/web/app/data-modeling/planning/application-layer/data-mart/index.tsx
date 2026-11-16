import { useWorkspace } from "@/hooks/use-workspace";

import { ModelingWorkspaceEmptyState } from "@/app/data-modeling/components/workspace-empty-state";
import { DataModelingDataMartPage } from "./data-mart-content";

export default function DataMartPage() {
  const { currentWorkspace } = useWorkspace();

  if (!currentWorkspace) return <ModelingWorkspaceEmptyState />;

  return <DataModelingDataMartPage projectCode={String(currentWorkspace.id)} />;
}
