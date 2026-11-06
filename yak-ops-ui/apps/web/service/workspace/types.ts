export interface WorkspaceRecord {
  id: string;
  projectId: string;
  ownerId: string;
  name: string;
  description?: string | null;
  createTime?: string;
  updateTime?: string;
  perm: number;
  roleName: string;
}

export interface WorkspaceCreatePayload {
  name: string;
  description?: string;
}

export interface WorkspaceMemberRecord {
  userId: string;
  userName: string;
}

export interface WorkspaceUserRecord {
  id: string;
  userName: string;
}
