import { useWorkspace } from "@/hooks/use-workspace";

import { DataIndicatorsWorkbench } from "./workbench";

export function DataModelingIndicatorsPage({ initialTab = 0 }: { initialTab?: number }) {
  const { currentWorkspace } = useWorkspace();

  if (!currentWorkspace) {
    return (
      <div className="flex min-h-[240px] h-full items-center justify-center rounded-md border border-dashed border-[var(--yak-components-panel-border)] bg-white text-sm text-[var(--yak-components-muted-text)]">
        请先选择工作空间
      </div>
    );
  }

  return (
    <DataIndicatorsWorkbench
      projectCode={String(currentWorkspace.id)}
      initialTab={initialTab}
    />
  );
}

export default DataModelingIndicatorsPage;
