import { createContext, useState, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";

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

const workspaceKey = (userId: string) => ["workspaces", userId] as const;

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const { currentUser } = useAuth();
  const userId = currentUser?.id ?? "";
  const client = useQueryClient();
  const [selected, setSelected] = useState<{ userId: string; id: string }>();

  const query = useQuery({
    queryKey: workspaceKey(userId),
    queryFn: () => listWorkspaces(userId),
    enabled: Boolean(userId),
  });
  const workspaces = query.data ?? [];
  const preferredId = userId
    ? window.localStorage.getItem(workspacePreferenceStorageKey(userId))
    : null;
  const selectedId = selected?.userId === userId ? selected.id : preferredId;
  const currentWorkspace =
    workspaces.find((item) => item.id === selectedId) ??
    workspaces.find((item) => item.id === preferredId) ??
    workspaces[0];

  const selectWorkspace = (id: string) => {
    if (!userId || !workspaces.some((item) => item.id === id)) return;
    window.localStorage.setItem(workspacePreferenceStorageKey(userId), id);
    setSelected({ userId, id });
  };

  const createWorkspace = async (payload: WorkspaceCreatePayload) => {
    if (!userId) throw new Error("请先登录");
    const workspace = await createWorkspaceRequest(payload, userId);
    client.setQueryData<WorkspaceRecord[]>(workspaceKey(userId), (items) => [
      workspace,
      ...(items ?? []).filter((item) => item.id !== workspace.id),
    ]);
    window.localStorage.setItem(workspacePreferenceStorageKey(userId), workspace.id);
    setSelected({ userId, id: workspace.id });
    return workspace;
  };

  const refreshWorkspaces = () =>
    userId
      ? client.fetchQuery({
          queryKey: workspaceKey(userId),
          queryFn: () => listWorkspaces(userId),
          staleTime: 0,
        })
      : Promise.resolve([]);

  const clearWorkspace = () => {
    setSelected(undefined);
    if (userId) client.removeQueries({ queryKey: workspaceKey(userId) });
  };

  return (
    <WorkspaceContext.Provider value={{
      workspaces, currentWorkspace,
      loading: Boolean(userId && query.isPending),
      error: Boolean(userId && query.isError),
      selectWorkspace, createWorkspace, refreshWorkspaces, clearWorkspace,
    }}>
      {children}
    </WorkspaceContext.Provider>
  );
}
