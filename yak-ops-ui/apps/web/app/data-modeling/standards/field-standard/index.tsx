import { useWorkspace } from "@/hooks/use-workspace";

import { ModelingWorkspaceEmptyState } from "@/app/data-modeling/components/workspace-empty-state";
import { DirectoryStandardsPage } from "../components/directory-standards-page";

export default function FieldStandardPage() {
  const { currentWorkspace } = useWorkspace();

  if (!currentWorkspace) return <ModelingWorkspaceEmptyState />;

  return <DirectoryStandardsPage projectCode={String(currentWorkspace.id)} module="FIELD" />;
}
