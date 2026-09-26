import { createContext, useEffect, useState, type ReactNode } from "react";

import { WORKSPACE_STORAGE_KEY } from "@/constants/workspace";
import { useAuth } from "@/hooks/use-auth";
import {
  createWorkspace as createWorkspaceRequest,
  listWorkspaces,
  type WorkspaceCreatePayload,
  type WorkspaceRecord,
} from "@/service/workspace";

export interface WorkspaceContextValue {
  workspaces: WorkspaceRecord[];
  currentWorkspace?: WorkspaceRecord;
  loading: boolean;
  selectWorkspace: (workspaceId: string) => void;
  createWorkspace: (payload: WorkspaceCreatePayload) => Promise<WorkspaceRecord>;
  refreshWorkspaces: () => Promise<WorkspaceRecord[]>;
  clearWorkspace: () => void;
}

export const WorkspaceContext = createContext<WorkspaceContextValue | null>(null);

const readStoredWorkspaceId = () => window.localStorage.getItem(WORKSPACE_STORAGE_KEY);

const persistWorkspace = (workspace?: WorkspaceRecord) => {
  if (workspace) {
    window.localStorage.setItem(WORKSPACE_STORAGE_KEY, workspace.id);
    return;
  }
  window.localStorage.removeItem(WORKSPACE_STORAGE_KEY);
};

const resolveCurrentWorkspace = (
  workspaces: WorkspaceRecord[],
  currentWorkspace?: WorkspaceRecord,
) => {
  const storedWorkspaceId = readStoredWorkspaceId();
  return (
    workspaces.find((workspace) => workspace.id === currentWorkspace?.id) ??
    workspaces.find((workspace) => workspace.id === storedWorkspaceId) ??
    workspaces[0]
  );
};

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const { currentUser } = useAuth();
  const [workspaces, setWorkspaces] = useState<WorkspaceRecord[]>([]);
  const [currentWorkspace, setCurrentWorkspace] = useState<WorkspaceRecord>();
  const [loading, setLoading] = useState(true);

  const clearWorkspace = () => {
    persistWorkspace(undefined);
    setWorkspaces([]);
    setCurrentWorkspace(undefined);
  };

  const applyWorkspaces = (nextWorkspaces: WorkspaceRecord[]) => {
    const nextCurrentWorkspace = resolveCurrentWorkspace(nextWorkspaces, currentWorkspace);
    setWorkspaces(nextWorkspaces);
    setCurrentWorkspace(nextCurrentWorkspace);
    persistWorkspace(nextCurrentWorkspace);
    return nextWorkspaces;
  };

  const refreshWorkspaces = async () => {
    const nextWorkspaces = await listWorkspaces();
    return applyWorkspaces(nextWorkspaces);
  };

  const selectWorkspace = (workspaceId: string) => {
    const nextWorkspace = workspaces.find((workspace) => workspace.id === workspaceId);
    if (!nextWorkspace || nextWorkspace.id === currentWorkspace?.id) return;
    persistWorkspace(nextWorkspace);
    setCurrentWorkspace(nextWorkspace);
  };

  const createWorkspace = async (payload: WorkspaceCreatePayload) => {
    const workspace = await createWorkspaceRequest(payload);
    setWorkspaces((current) => [workspace, ...current.filter((item) => item.id !== workspace.id)]);
    persistWorkspace(workspace);
    setCurrentWorkspace(workspace);
    return workspace;
  };

  useEffect(() => {
    let active = true;

    if (!currentUser) {
      persistWorkspace(undefined);
      setWorkspaces([]);
      setCurrentWorkspace(undefined);
      setLoading(false);
      return () => {
        active = false;
      };
    }

    setLoading(true);
    listWorkspaces()
      .then((nextWorkspaces) => {
        if (!active) return;
        const nextCurrentWorkspace = resolveCurrentWorkspace(nextWorkspaces);
        setWorkspaces(nextWorkspaces);
        setCurrentWorkspace(nextCurrentWorkspace);
        persistWorkspace(nextCurrentWorkspace);
      })
      .catch(() => {
        if (!active) return;
        persistWorkspace(undefined);
        setWorkspaces([]);
        setCurrentWorkspace(undefined);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [currentUser?.id]);

  return (
    <WorkspaceContext.Provider
      value={{
        workspaces,
        currentWorkspace,
        loading,
        selectWorkspace,
        createWorkspace,
        refreshWorkspaces,
        clearWorkspace,
      }}
    >
      {children}
    </WorkspaceContext.Provider>
  );
}
