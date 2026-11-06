// Only a user-specific UI preference. No Workspace header is sent to the server.
export const workspacePreferenceStorageKey = (userId: string) =>
  "yak-ops.project-workspace.user." + userId;
