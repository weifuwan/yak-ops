import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type {
  WorkspaceCreatePayload,
  WorkspaceMemberRecord,
  WorkspaceRecord,
  WorkspaceUserRecord,
} from "./types";

const PROJECT_API = YAK_OPS_API_PREFIX + "/projects";
const USER_API = YAK_OPS_API_PREFIX + "/users";

interface BackendProject {
  id: number;
  code: string | number;
  userId: number;
  name: string;
  description?: string | null;
  createTime?: string;
  updateTime?: string;
  perm?: number;
}

interface BackendUser {
  id: number;
  userName: string;
}

const toWorkspace = (project: BackendProject, userId: string): WorkspaceRecord => ({
  id: String(project.code),
  projectId: String(project.id),
  ownerId: String(project.userId),
  name: project.name,
  description: project.description,
  createTime: project.createTime,
  updateTime: project.updateTime,
  perm: project.perm ?? 0,
  roleName: String(project.userId) === userId ? "所有者" : "已授权",
});

// The UI calls these Project APIs directly; there is no separate Workspace resource.
export const listWorkspaces = async (userId: string): Promise<WorkspaceRecord[]> => {
  const projects = await HttpUtils.getData<BackendProject[]>(PROJECT_API + "/created-and-authed");
  return projects.map((project) => toWorkspace(project, userId));
};

export const createWorkspace = async (
  payload: WorkspaceCreatePayload,
  userId: string,
): Promise<WorkspaceRecord> => {
  const project = await HttpUtils.postData<BackendProject>(PROJECT_API, undefined, {
    body: new URLSearchParams({
      projectName: payload.name,
      description: payload.description ?? "",
    }),
  });
  return toWorkspace(project, userId);
};

export const updateWorkspace = async (
  workspaceId: string,
  payload: WorkspaceCreatePayload,
): Promise<void> => {
  await HttpUtils.putData<unknown>(PROJECT_API + "/" + encodeURIComponent(workspaceId), undefined, {
    body: new URLSearchParams({
      projectName: payload.name,
      description: payload.description ?? "",
    }),
  });
};

export const deleteWorkspace = async (workspaceId: string): Promise<void> => {
  await HttpUtils.deleteData<unknown>(PROJECT_API + "/" + encodeURIComponent(workspaceId));
};

export const getWorkspaceMembers = async (workspaceId: string): Promise<WorkspaceMemberRecord[]> => {
  const users = await HttpUtils.getData<BackendUser[]>(
    PROJECT_API + "/authed-user?projectCode=" + encodeURIComponent(workspaceId),
  );
  return users.map((user) => ({ userId: String(user.id), userName: user.userName }));
};

export const listAssignableUsers = async (): Promise<WorkspaceUserRecord[]> => {
  const users = await HttpUtils.getData<BackendUser[]>(USER_API + "/list-all");
  return users.map((user) => ({ id: String(user.id), userName: user.userName }));
};

// The existing by-code grant provides writable Project permission.
// Read-only grants and permission changes require an admin and Project numeric ID.
export const addWorkspaceMember = async (workspaceId: string, userId: string): Promise<void> => {
  await HttpUtils.postData<unknown>(USER_API + "/grant-project-by-code", undefined, {
    body: new URLSearchParams({ projectCode: workspaceId, userId }),
  });
};

// The backend restricts this endpoint to system administrators.
export const removeWorkspaceMember = async (workspaceId: string, userId: string): Promise<void> => {
  await HttpUtils.postData<unknown>(USER_API + "/revoke-project", undefined, {
    body: new URLSearchParams({ projectCode: workspaceId, userId }),
  });
};

export type {
  WorkspaceCreatePayload,
  WorkspaceMemberRecord,
  WorkspaceRecord,
  WorkspaceUserRecord,
} from "./types";
