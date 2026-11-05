/** Cache isolation: authenticated user identity is part of every detail key. */
export const detailQueryKeys = {
  scope: (scope: string) => ["data-sync-detail", scope] as const,
  task: (scope: string, taskId: string) =>
    [...detailQueryKeys.scope(scope), "task", taskId] as const,
  dataSources: (scope: string, sourceId: string, targetId: string) =>
    [...detailQueryKeys.scope(scope), "sources", sourceId, targetId] as const,
  updatedBy: (scope: string, userId: string) =>
    [...detailQueryKeys.scope(scope), "user", userId] as const,
  activeInstance: (scope: string, taskId: string) =>
    [...detailQueryKeys.scope(scope), "active-instance", taskId] as const,
  instances: (scope: string, taskId: string) =>
    [...detailQueryKeys.scope(scope), "instances", taskId] as const,
  instancePage: (scope: string, taskId: string, page: number, status: string) =>
    [...detailQueryKeys.instances(scope, taskId), page, status] as const,
  instance: (scope: string, taskId: string, instanceId: string) =>
    [...detailQueryKeys.scope(scope), "instance", taskId, instanceId] as const,
};
