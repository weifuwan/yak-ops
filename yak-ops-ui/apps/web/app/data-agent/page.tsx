import { useRef, useState } from "react";

import AppShell from "../layout/AppShell";

import { DataAgentWelcome } from "./components/DataAgentWelcome";
import { DataAgentWorkspace } from "./components/DataAgentWorkspace";
import { ParticleTransition } from "./components/ParticleTransition";

const DATA_AGENT_PRODUCT_LABEL = "Data Agent";
const DATA_AGENT_WORKSPACE_KEY = "yak-ops:data-agent:workspace-open";

type DataAgentPagePhase = "welcome" | "transitioning" | "workspace";

function readWorkspacePreference() {
  try {
    return typeof window !== "undefined" &&
      window.sessionStorage.getItem(DATA_AGENT_WORKSPACE_KEY) === "true";
  } catch {
    return false;
  }
}

export function DataAgentPage() {
  const [phase, setPhase] = useState<DataAgentPagePhase>(() =>
    readWorkspacePreference() ? "workspace" : "welcome",
  );
  const [initialPrompt, setInitialPrompt] = useState("");
  const transitionStartedRef = useRef(false);

  function enterWorkspace(prompt = "") {
    if (transitionStartedRef.current) return;

    transitionStartedRef.current = true;
    setInitialPrompt(prompt);

    try {
      window.sessionStorage.setItem(DATA_AGENT_WORKSPACE_KEY, "true");
    } catch {
      // Keep the workspace usable when browser storage is unavailable.
    }

    setPhase("transitioning");
  }

  function finishTransition() {
    setPhase("workspace");
  }

  return (
    <div className="relative isolate h-dvh w-full overflow-hidden bg-[#f4f6f6]">
      {phase !== "welcome" ? (
        <div
          aria-hidden={phase === "transitioning"}
          className="absolute inset-0 z-0"
          data-agent-transition-workspace=""
          data-entering={phase === "transitioning" ? "true" : "false"}
          ref={(element) => {
            if (element) element.inert = phase === "transitioning";
          }}
        >
          <AppShell
            productLabel={DATA_AGENT_PRODUCT_LABEL}
            productPath="/data-agent"
          >
            <DataAgentWorkspace initialPrompt={initialPrompt} />
          </AppShell>
        </div>
      ) : null}

      {phase !== "workspace" ? (
        <div
          aria-hidden={phase === "transitioning"}
          className="data-agent-transition-welcome absolute inset-0 z-10"
          data-exiting={phase === "transitioning" ? "true" : "false"}
          ref={(element) => {
            if (element) element.inert = phase === "transitioning";
          }}
        >
          <DataAgentWelcome onEnter={enterWorkspace} />
        </div>
      ) : null}

      {phase === "transitioning" ? (
        <ParticleTransition
          active
          origin={{ x: 0.5, y: 0.5 }}
          direction="both"
          particleCount={760}
          duration={1600}
          onComplete={finishTransition}
        />
      ) : null}
    </div>
  );
}

export default DataAgentPage;
