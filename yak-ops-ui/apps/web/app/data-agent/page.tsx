import AppShell from "../layout/AppShell";

import { DataAgentWorkspace } from "./components/DataAgentWorkspace";
import "./data-agent.css";

const DATA_AGENT_PRODUCT_LABEL = "Data Agent";

export function DataAgentPage() {
  return (
    <AppShell
      productLabel={DATA_AGENT_PRODUCT_LABEL}
      productPath="/data-agent"
    >
      <DataAgentWorkspace />
    </AppShell>
  );
}

export default DataAgentPage;
