import { useWorkspace } from "@/hooks/use-workspace";
import { ModelingWorkspaceEmptyState } from "@/app/data-modeling/components/workspace-empty-state";
import { DimensionModelingWorkbench } from "./workbench";

export default function DimensionModelingPage() {
  const { currentWorkspace } = useWorkspace();

  if (!currentWorkspace) return <ModelingWorkspaceEmptyState />;

  return (
    <div className="h-full min-h-0">
      <DimensionModelingWorkbench projectCode={String(currentWorkspace.id)} />
    </div>
  );
}
