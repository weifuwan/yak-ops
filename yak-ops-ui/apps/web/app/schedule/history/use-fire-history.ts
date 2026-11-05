import { useQuery } from "@tanstack/react-query";
import { listScheduleFires, type ScheduleFireOutcome } from "@/service/schedule";
import { scheduleQueryKeys, useScheduleScope, visiblePollInterval } from "../query-keys";

export interface FireHistoryFilters {
  pageNo: number;
  outcome: ScheduleFireOutcome | "ALL";
  from: string;
  until: string;
}

export function useFireHistory(
  scheduleId: string,
  { pageNo, outcome, from, until }: FireHistoryFilters,
) {
  const { userId } = useScheduleScope();
  const invalidRange = Boolean(from && until && from >= until);
  return useQuery({
    queryKey: scheduleQueryKeys.fires(
      userId,
      scheduleId,
      pageNo,
      outcome,
      from,
      until,
    ),
    enabled: Boolean(userId && scheduleId && !invalidRange),
    queryFn: ({ signal }) =>
      listScheduleFires(
        scheduleId,
        {
          pageNo,
          pageSize: 20,
          outcome: outcome === "ALL" ? undefined : outcome,
          from: from ? new Date(from + "Z").toISOString() : undefined,
          until: until ? new Date(until + "Z").toISOString() : undefined,
        },
        signal,
      ),
    retry: false,
    refetchInterval: visiblePollInterval,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: false,
  });
}
