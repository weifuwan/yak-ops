import { useWorkspace } from "@/hooks/use-workspace";

import { ModelingWorkspaceEmptyState } from "@/app/data-modeling/components/workspace-empty-state";
import { DirectoryStandardsPage } from "../components/directory-standards-page";

export default function UnitStandardPage() {
  const { currentWorkspace } = useWorkspace();

  if (!currentWorkspace) return <ModelingWorkspaceEmptyState />;

  return <DirectoryStandardsPage projectCode={String(currentWorkspace.id)} module="UNIT" />;
}
