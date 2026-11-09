import { DataDevelopmentResourcePanel } from "./resource-panel";
import { DataDevelopmentWorkbench } from "./workbench";
import { DataDevelopmentWorkbenchProvider } from "./workbench-context";

export function DataDevelopmentPage() {
  return (
    <DataDevelopmentWorkbenchProvider>
      <div className="flex h-full min-h-0 overflow-hidden bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)]">
        <DataDevelopmentResourcePanel />
        <DataDevelopmentWorkbench />
      </div>
    </DataDevelopmentWorkbenchProvider>
  );
}

export default DataDevelopmentPage;
