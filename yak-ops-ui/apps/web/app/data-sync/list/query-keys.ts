/** Each list key includes the authenticated user identity. */
export const listQueryKeys = {
  scope: (scope: string) => ["data-sync-list", scope] as const,
  tasks: (scope: string) => [...listQueryKeys.scope(scope), "tasks"] as const,
  dataSources: (scope: string) => [...listQueryKeys.scope(scope), "data-sources"] as const,
  users: (scope: string) => [...listQueryKeys.scope(scope), "users"] as const,
  schedules: (scope: string) => [...listQueryKeys.scope(scope), "schedules"] as const,
  instances: (scope: string) => [...listQueryKeys.scope(scope), "instances"] as const,
};
