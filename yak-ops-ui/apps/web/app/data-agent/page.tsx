import { useState } from "react";

import AppShell from "../layout/AppShell";

import { DataAgentWelcome } from "./components/DataAgentWelcome";
import { DataAgentWorkspace } from "./components/DataAgentWorkspace";

const DATA_AGENT_PRODUCT_LABEL = "Data Agent";

export function DataAgentPage() {
  const [workspaceOpen, setWorkspaceOpen] = useState(false);
  const [initialPrompt, setInitialPrompt] = useState("");

  function enterWorkspace(prompt = "") {
    setInitialPrompt(prompt);
    setWorkspaceOpen(true);
  }

  if (!workspaceOpen) {
    return <DataAgentWelcome onEnter={enterWorkspace} />;
  }

  return (
    <AppShell
      productLabel={DATA_AGENT_PRODUCT_LABEL}
      productPath="/data-agent"
    >
      <DataAgentWorkspace initialPrompt={initialPrompt} />
    </AppShell>
  );
}

export default DataAgentPage;
