import { useMemo } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { listTaskSchedules, type ScheduleSummary } from "@/service/schedule";
import { scheduleQueryKeys, useScheduleScope, visiblePollInterval } from "./query-keys";

/** One bounded batch per visible task page, isolated by user, Workspace and target IDs. */
export function useTaskSchedules(targetIds: string[]) {
  const { userId } = useScheduleScope();
  const queryClient = useQueryClient();
  const idsKey = JSON.stringify([...new Set(targetIds)].sort());
  const ids = useMemo(() => JSON.parse(idsKey) as string[], [idsKey]);
  const key = scheduleQueryKeys.targets(userId, ids);
  const enabled = Boolean(userId && ids.length);
  const query = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => listTaskSchedules(ids, signal),
    enabled,
    retry: false,
    refetchInterval: visiblePollInterval,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: false,
  });
  const records = useMemo(
    () => new Map<string, ScheduleSummary>((query.data ?? []).map((row) => [row.targetId, row])),
    [query.data],
  );
  return {
    records,
    loading: enabled && query.isPending,
    error: query.isError,
    refresh: () => {
      void queryClient.invalidateQueries({ queryKey: key });
    },
  };
}
