import { useState } from "react";

import AppShell from "../layout/AppShell";

import { DataAgentWelcome } from "./components/DataAgentWelcome";
import { DataAgentWorkspace } from "./components/DataAgentWorkspace";

const DATA_AGENT_PRODUCT_LABEL = "Data Agent";
const DATA_AGENT_WORKSPACE_KEY = "yak-ops:data-agent:workspace-open";

function readWorkspacePreference() {
  try {
    return typeof window !== "undefined" &&
      window.sessionStorage.getItem(DATA_AGENT_WORKSPACE_KEY) === "true";
  } catch {
    return false;
  }
}

export function DataAgentPage() {
  const [workspaceOpen, setWorkspaceOpen] = useState(readWorkspacePreference);
  const [initialPrompt, setInitialPrompt] = useState("");

  function enterWorkspace(prompt = "") {
    setInitialPrompt(prompt);
    try {
      window.sessionStorage.setItem(DATA_AGENT_WORKSPACE_KEY, "true");
    } catch {
      // Keep the workspace usable when browser storage is unavailable.
    }
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
