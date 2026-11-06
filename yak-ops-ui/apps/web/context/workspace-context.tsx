import { createContext, useCallback, useEffect, useState, type ReactNode } from "react";

import { workspacePreferenceStorageKey } from "@/constants/workspace";
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
  error: boolean;
  selectWorkspace: (workspaceId: string) => void;
  createWorkspace: (payload: WorkspaceCreatePayload) => Promise<WorkspaceRecord>;
  refreshWorkspaces: () => Promise<WorkspaceRecord[]>;
  clearWorkspace: () => void;
}

export const WorkspaceContext = createContext<WorkspaceContextValue | null>(null);

const resolveWorkspace = (items: WorkspaceRecord[], userId: string, activeId?: string) => {
  const preferred = window.localStorage.getItem(workspacePreferenceStorageKey(userId));
  return items.find((item) => item.id === activeId) ??
    items.find((item) => item.id === preferred) ??
    items[0];
};

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const { currentUser } = useAuth();
  const userId = currentUser?.id;
  const [workspaces, setWorkspaces] = useState<WorkspaceRecord[]>([]);
  const [currentWorkspace, setCurrentWorkspace] = useState<WorkspaceRecord>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const clearWorkspace = useCallback(() => {
    setWorkspaces([]);
    setCurrentWorkspace(undefined);
    setError(false);
  }, []);

  const refreshWorkspaces = useCallback(async () => {
    if (!userId) return [];
    const items = await listWorkspaces(userId);
    setWorkspaces(items);
    setError(false);
    setCurrentWorkspace((current) => resolveWorkspace(items, userId, current?.id));
    return items;
  }, [userId]);

  const selectWorkspace = (workspaceId: string) => {
    const item = workspaces.find((workspace) => workspace.id === workspaceId);
    if (!item || !userId) return;
    window.localStorage.setItem(workspacePreferenceStorageKey(userId), item.id);
    setCurrentWorkspace(item);
  };

  const createWorkspace = async (payload: WorkspaceCreatePayload) => {
    if (!userId) throw new Error("请先登录");
    const workspace = await createWorkspaceRequest(payload, userId);
    const items = await listWorkspaces(userId);
    setWorkspaces(items);
    setError(false);
    const selected = items.find((item) => item.id === workspace.id) ?? workspace;
    window.localStorage.setItem(workspacePreferenceStorageKey(userId), selected.id);
    setCurrentWorkspace(selected);
    return selected;
  };

  useEffect(() => {
    let active = true;
    setWorkspaces([]);
    setCurrentWorkspace(undefined);
    setError(false);
    if (!userId) {
      setLoading(false);
      return () => { active = false; };
    }
    setLoading(true);
    listWorkspaces(userId).then((items) => {
      if (!active) return;
      setWorkspaces(items);
      setCurrentWorkspace(resolveWorkspace(items, userId));
    }).catch(() => {
      if (active) setError(true);
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [userId]);

  return (
    <WorkspaceContext.Provider value={{
      workspaces, currentWorkspace, loading, error,
      selectWorkspace, createWorkspace, refreshWorkspaces, clearWorkspace,
    }}>
      {children}
    </WorkspaceContext.Provider>
  );
}
