import { useQuery } from "@tanstack/react-query";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { getDataIntegrationOverview, getDataSyncExecutionStats } from "@/service/data-sync/overview";
import type { ExecutionRange } from "./metrics";

export function useDataIntegrationOverview() {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const userId = currentUser?.id ?? "";
  const projectCode = currentWorkspace?.id ?? "";

  return useQuery({
    queryKey: ["data-integration", "overview", userId, projectCode],
    queryFn: ({ signal }) => getDataIntegrationOverview(projectCode, signal),
    enabled: Boolean(userId && projectCode),
    staleTime: 15_000,
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
    retry: false,
  });
}

export function useDataSyncExecutionMetrics(range: ExecutionRange) {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const userId = currentUser?.id ?? "";
  const projectCode = currentWorkspace?.id ?? "";
  return useQuery({
    queryKey: ["data-integration", "executions", userId, projectCode, range.startDate, range.endDate],
    queryFn: ({ signal }) =>
      getDataSyncExecutionStats(projectCode, range.startDate, range.endDate, signal),
    enabled: Boolean(userId && projectCode),
    staleTime: 30_000,
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
    retry: false,
  });
}
