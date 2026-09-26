import HttpUtils from "@/service/http/HttpUtils";

import type {
  WorkspaceCreatePayload,
  WorkspaceMemberCreatePayload,
  WorkspaceMemberRecord,
  WorkspaceMemberRolePayload,
  WorkspaceRecord,
} from "./types";

const WORKSPACE_API_PREFIX = "/api/v1/workspaces";

const WITHOUT_WORKSPACE_HEADER = {
  workspaceHeader: "omit" as const,
};

export const listWorkspaces = (): Promise<WorkspaceRecord[]> =>
  HttpUtils.getData<WorkspaceRecord[]>(WORKSPACE_API_PREFIX, WITHOUT_WORKSPACE_HEADER);

export const createWorkspace = (payload: WorkspaceCreatePayload): Promise<WorkspaceRecord> =>
  HttpUtils.postData<WorkspaceRecord>(WORKSPACE_API_PREFIX, payload, WITHOUT_WORKSPACE_HEADER);

export const getWorkspaceMembers = (workspaceId: string): Promise<WorkspaceMemberRecord[]> =>
  HttpUtils.getData<WorkspaceMemberRecord[]>(
    `${WORKSPACE_API_PREFIX}/${workspaceId}/members`,
    WITHOUT_WORKSPACE_HEADER,
  );

export const addWorkspaceMember = (
  workspaceId: string,
  payload: WorkspaceMemberCreatePayload,
): Promise<WorkspaceMemberRecord> =>
  HttpUtils.postData<WorkspaceMemberRecord>(
    `${WORKSPACE_API_PREFIX}/${workspaceId}/members`,
    payload,
    WITHOUT_WORKSPACE_HEADER,
  );

export const updateWorkspaceMemberRole = (
  workspaceId: string,
  userId: string,
  payload: WorkspaceMemberRolePayload,
): Promise<WorkspaceMemberRecord> =>
  HttpUtils.putData<WorkspaceMemberRecord>(
    `${WORKSPACE_API_PREFIX}/${workspaceId}/members/${userId}`,
    payload,
    WITHOUT_WORKSPACE_HEADER,
  );

export const removeWorkspaceMember = async (workspaceId: string, userId: string): Promise<void> => {
  await HttpUtils.deleteData<boolean>(
    `${WORKSPACE_API_PREFIX}/${workspaceId}/members/${userId}`,
    undefined,
    WITHOUT_WORKSPACE_HEADER,
  );
};

export type {
  WorkspaceCreatePayload,
  WorkspaceMemberCreatePayload,
  WorkspaceMemberRecord,
  WorkspaceMemberRolePayload,
  WorkspaceRecord,
  WorkspaceRole,
} from "./types";
