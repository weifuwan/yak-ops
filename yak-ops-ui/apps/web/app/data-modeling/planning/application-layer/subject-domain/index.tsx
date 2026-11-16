import { useWorkspace } from "@/hooks/use-workspace";

import { ModelingWorkspaceEmptyState } from "@/app/data-modeling/components/workspace-empty-state";
import { DataModelingSubjectDomainPage } from "./subject-domain-content";

export default function SubjectDomainPage() {
  const { currentWorkspace } = useWorkspace();

  if (!currentWorkspace) return <ModelingWorkspaceEmptyState />;

  return <DataModelingSubjectDomainPage projectCode={String(currentWorkspace.id)} />;
}
