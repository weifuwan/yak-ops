import { createContext, useEffect, useState, type ReactNode } from "react";

import { WORKSPACE_STORAGE_KEY, workspacePreferenceStorageKey } from "@/constants/workspace";
import { clearTaskDrafts } from "@/service/data-sync/draft";
import { useAuth } from "@/hooks/use-auth";
import { useLeaveGuard } from "./leave-guard-context";
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
  selectWorkspace: (workspaceId: string) => Promise<void>;
  createWorkspace: (payload: WorkspaceCreatePayload) => Promise<WorkspaceRecord | undefined>;
  refreshWorkspaces: () => Promise<WorkspaceRecord[]>;
  clearWorkspace: () => void;
}

export const WorkspaceContext = createContext<WorkspaceContextValue | null>(null);

const readActiveWorkspaceId = () => window.localStorage.getItem(WORKSPACE_STORAGE_KEY);

const readWorkspacePreference = (userId: string) =>
  window.localStorage.getItem(workspacePreferenceStorageKey(userId));

const persistActiveWorkspace = (workspace?: WorkspaceRecord) => {
  if (workspace) {
    window.localStorage.setItem(WORKSPACE_STORAGE_KEY, workspace.id);
    return;
  }
  window.localStorage.removeItem(WORKSPACE_STORAGE_KEY);
};

const persistWorkspacePreference = (userId: string, workspace?: WorkspaceRecord) => {
  const storageKey = workspacePreferenceStorageKey(userId);
  if (workspace) {
    window.localStorage.setItem(storageKey, workspace.id);
    return;
  }
  window.localStorage.removeItem(storageKey);
};

const persistWorkspaceSelection = (workspace?: WorkspaceRecord, userId?: string) => {
  persistActiveWorkspace(workspace);
  if (userId) persistWorkspacePreference(userId, workspace);
};

const resolveCurrentWorkspace = (
  workspaces: WorkspaceRecord[],
  userId: string,
  currentWorkspace?: WorkspaceRecord,
  legacyWorkspaceId?: string | null,
) => {
  const preferredWorkspaceId = readWorkspacePreference(userId);
  return (
    workspaces.find((workspace) => workspace.id === currentWorkspace?.id) ??
    workspaces.find((workspace) => workspace.id === preferredWorkspaceId) ??
    workspaces.find((workspace) => workspace.id === legacyWorkspaceId) ??
    workspaces[0]
  );
};

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const { currentUser, loading: authLoading } = useAuth();
  const { confirmLeave } = useLeaveGuard();
  const [workspaces, setWorkspaces] = useState<WorkspaceRecord[]>([]);
  const [currentWorkspace, setCurrentWorkspace] = useState<WorkspaceRecord>();
  const [loading, setLoading] = useState(true);

  const clearWorkspace = () => {
    clearTaskDrafts();
    persistActiveWorkspace(undefined);
    setWorkspaces([]);
    setCurrentWorkspace(undefined);
  };

  const applyWorkspaces = (nextWorkspaces: WorkspaceRecord[]) => {
    const userId = currentUser?.id;
    const nextCurrentWorkspace = userId
      ? resolveCurrentWorkspace(nextWorkspaces, userId, currentWorkspace)
      : undefined;
    setWorkspaces(nextWorkspaces);
    setCurrentWorkspace(nextCurrentWorkspace);
    persistWorkspaceSelection(nextCurrentWorkspace, userId);
    return nextWorkspaces;
  };

  const refreshWorkspaces = async () => {
    const nextWorkspaces = await listWorkspaces();
    return applyWorkspaces(nextWorkspaces);
  };

  const selectWorkspace = async (workspaceId: string) => {
    const nextWorkspace = workspaces.find((workspace) => workspace.id === workspaceId);
    if (!nextWorkspace || nextWorkspace.id === currentWorkspace?.id) return;
    if (!(await confirmLeave())) return;
    clearTaskDrafts();
    persistWorkspaceSelection(nextWorkspace, currentUser?.id);
    setCurrentWorkspace(nextWorkspace);
  };

  const createWorkspace = async (payload: WorkspaceCreatePayload) => {
    if (!(await confirmLeave())) return undefined;
    const workspace = await createWorkspaceRequest(payload);
    clearTaskDrafts();
    setWorkspaces((current) => [workspace, ...current.filter((item) => item.id !== workspace.id)]);
    persistWorkspaceSelection(workspace, currentUser?.id);
    setCurrentWorkspace(workspace);
    return workspace;
  };

  useEffect(() => {
    let active = true;

    if (authLoading) {
      setLoading(true);
      return () => {
        active = false;
      };
    }

    if (!currentUser) {
      persistActiveWorkspace(undefined);
      setWorkspaces([]);
      setCurrentWorkspace(undefined);
      setLoading(false);
      return () => {
        active = false;
      };
    }

    const legacyWorkspaceId = readActiveWorkspaceId();
    persistActiveWorkspace(undefined);
    setWorkspaces([]);
    setCurrentWorkspace(undefined);
    setLoading(true);

    listWorkspaces()
      .then((nextWorkspaces) => {
        if (!active) return;
        const nextCurrentWorkspace = resolveCurrentWorkspace(
          nextWorkspaces,
          currentUser.id,
          undefined,
          legacyWorkspaceId,
        );
        setWorkspaces(nextWorkspaces);
        setCurrentWorkspace(nextCurrentWorkspace);
        persistWorkspaceSelection(nextCurrentWorkspace, currentUser.id);
      })
      .catch(() => {
        if (!active) return;
        persistActiveWorkspace(undefined);
        setWorkspaces([]);
        setCurrentWorkspace(undefined);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [authLoading, currentUser?.id]);

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
