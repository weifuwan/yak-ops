import { FolderOpen } from "lucide-react";

export function ModelingWorkspaceEmptyState() {
  return (
    <div className="flex min-h-[240px] flex-1 items-center justify-center rounded-md border border-dashed border-[var(--yak-components-panel-border)] bg-white text-sm text-[var(--yak-components-muted-text)]">
      <span className="inline-flex items-center gap-2"><FolderOpen size={15} aria-hidden="true" />请先选择工作空间</span>
    </div>
  );
}
