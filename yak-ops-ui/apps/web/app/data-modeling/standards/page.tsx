import { useWorkspace } from "@/hooks/use-workspace";

import { DirectoryStandardsPage } from "./directory-standards-page";
import { DictionaryStandardsPage } from "./dictionary-standards-page";
import type { StandardModule } from "./types";

const TAB_MODULE: Record<number, StandardModule> = {
  1: "FIELD",
  3: "CODE",
  6: "UNIT",
};

export function DataModelingStandardsPage({ initialTab = 1 }: { initialTab?: number }) {
  const { currentWorkspace } = useWorkspace();

  if (!currentWorkspace) {
    return (
      <div className="flex min-h-[240px] flex-1 items-center justify-center rounded-md border border-dashed border-[var(--yak-components-panel-border)] bg-white text-sm text-[var(--yak-components-muted-text)]">
        请先选择工作空间
      </div>
    );
  }

  const module = TAB_MODULE[initialTab];
  return module
    ? <DirectoryStandardsPage projectCode={currentWorkspace.id} module={module} />
    : <DictionaryStandardsPage projectCode={currentWorkspace.id} />;
}

export default DataModelingStandardsPage;
