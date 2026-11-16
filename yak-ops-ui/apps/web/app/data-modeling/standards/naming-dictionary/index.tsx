import { useWorkspace } from "@/hooks/use-workspace";

import { ModelingWorkspaceEmptyState } from "@/app/data-modeling/components/workspace-empty-state";
import { DictionaryStandardsPage } from "./dictionary-standards-content";

export default function NamingDictionaryPage() {
  const { currentWorkspace } = useWorkspace();

  if (!currentWorkspace) return <ModelingWorkspaceEmptyState />;

  return <DictionaryStandardsPage projectCode={String(currentWorkspace.id)} />;
}
