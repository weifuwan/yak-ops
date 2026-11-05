import { useAuth } from "@/hooks/use-auth";

export const scheduleQueryKeys = {
  all: ["schedule"] as const,
  tasks: () => [...scheduleQueryKeys.all, "tasks"] as const,
  targets: (userId: string, ids: readonly string[]) =>
    [...scheduleQueryKeys.tasks(), userId, ...ids] as const,
  detail: (userId: string, targetId: string) =>
    [...scheduleQueryKeys.all, "detail", userId, targetId] as const,
  preview: (userId: string, cron: string, zone: string) =>
    [...scheduleQueryKeys.all, "preview", userId, cron, zone] as const,
  fires: (
    userId: string,
    scheduleId: string,
    pageNo: number,
    outcome: string,
    from: string,
    until: string,
  ) =>
    [
      ...scheduleQueryKeys.all,
      "fires",
      userId,
      scheduleId,
      pageNo,
      outcome,
      from,
      until,
    ] as const,
};

export function useScheduleScope() {
  const { currentUser } = useAuth();
  return { userId: currentUser?.id ?? "" };
}

/** TanStack Query pauses hidden-tab requests while the polling timer stays active. */
export const visiblePollInterval = () => 5000;
