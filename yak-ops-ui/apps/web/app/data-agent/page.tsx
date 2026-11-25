import { useCallback } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";

import AppShell from "../layout/AppShell";

import { DataAgentWelcome } from "./components/DataAgentWelcome";
import { DataAgentWorkspace } from "./components/DataAgentWorkspace";

const DATA_AGENT_PRODUCT_LABEL = "Data Agent";

type DataAgentLocationState = {
  initialPrompt?: string;
  playInitialParticleTransition?: boolean;
};

export function DataAgentPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { sessionId } = useParams<{ sessionId: string }>();
  const locationState = location.state as DataAgentLocationState | null;
  const initialPrompt = locationState?.initialPrompt ?? "";

  const handleSessionCreated = useCallback(
    (createdSessionId: string) => {
      navigate("/data-agent/sessions/" + encodeURIComponent(createdSessionId), {
        replace: true,
      });
    },
    [navigate],
  );

  const handleNewConversation = useCallback(() => {
    navigate("/data-agent/new");
  }, [navigate]);

  const handleSelectConversation = useCallback(
    (selectedSessionId: string) => {
      navigate("/data-agent/sessions/" + encodeURIComponent(selectedSessionId));
    },
    [navigate],
  );

  if (location.pathname === "/data-agent") {
    return (
      <DataAgentWelcome
        onEnter={(prompt = "") =>
          navigate("/data-agent/new", {
            state: { initialPrompt: prompt, playInitialParticleTransition: true },
          })
        }
      />
    );
  }

  return (
    <AppShell
      productLabel={DATA_AGENT_PRODUCT_LABEL}
      productPath="/data-agent"
    >
      <DataAgentWorkspace
        sessionId={sessionId ?? null}
        initialPrompt={initialPrompt}
        enableInitialParticleTransition={Boolean(
          locationState?.playInitialParticleTransition && !sessionId,
        )}
        onSessionCreated={handleSessionCreated}
        onNewConversation={handleNewConversation}
        onSelectConversation={handleSelectConversation}
      />
    </AppShell>
  );
}

export default DataAgentPage;
