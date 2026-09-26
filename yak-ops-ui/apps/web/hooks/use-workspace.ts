import { useContext } from "react";

import { WorkspaceContext } from "@/context/workspace-context";

export function useWorkspace() {
  const context = useContext(WorkspaceContext);
  if (!context) {
    throw new Error("useWorkspace must be used inside WorkspaceProvider");
  }
  return context;
}
