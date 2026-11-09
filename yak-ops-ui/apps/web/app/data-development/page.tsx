import { useWorkspace } from "@/hooks/use-workspace";

import AppShell from "../layout/AppShell";
import { DATA_DEVELOPMENT_PRODUCT_LABEL } from "../layout/navigation";
import { DataDevelopmentLayout } from "./layout/data-development-layout";
import { DataDevelopmentWorkbenchProvider } from "./workbench-context";

export function DataDevelopmentPage() {
  const { currentWorkspace } = useWorkspace();

  return (
    <AppShell
      productLabel={DATA_DEVELOPMENT_PRODUCT_LABEL}
      productPath="/data-development"
    >
      <DataDevelopmentWorkbenchProvider
        key={currentWorkspace?.id ?? "no-project"}
      >
        <DataDevelopmentLayout />
      </DataDevelopmentWorkbenchProvider>
    </AppShell>
  );
}

export default DataDevelopmentPage;
