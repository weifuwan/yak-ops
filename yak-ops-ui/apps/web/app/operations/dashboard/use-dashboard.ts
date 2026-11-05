import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import type { DataSyncType } from "@/service/data-sync";
import {
  getDataSyncOperations,
  type DataSyncOperationsRange,
} from "@/service/data-sync/operations";

export const operationsQueryKey = (
  userId: string,
  syncType: DataSyncType,
  range: DataSyncOperationsRange,
) => ["operations", "dashboard", userId, syncType, range] as const;

/** One server-owned dashboard snapshot per user, sync type, and range. */
export function useOperationsDashboard(syncType: DataSyncType, range: DataSyncOperationsRange) {
  const { currentUser } = useAuth();
  const userId = currentUser?.id ?? "";

  return useQuery({
    queryKey: operationsQueryKey(userId, syncType, range),
    queryFn: ({ signal }) => getDataSyncOperations(syncType, range, signal),
    enabled: Boolean(userId),
    retry: false,
    refetchInterval: 30000,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
}
