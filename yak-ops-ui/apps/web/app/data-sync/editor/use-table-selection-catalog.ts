import { useInfiniteQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { pageDataSourceTables } from "@/service/datasource";
import type { TransferFilters } from "./table-transfer-panel";

export function useTableSelectionCatalog(dataSourceId: string, filters: TransferFilters) {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const query = useInfiniteQuery({
    queryKey: [
      "data-sync-selection-catalog",
      currentUser?.id,
      currentWorkspace?.id,
      dataSourceId,
      filters,
    ],
    initialPageParam: 0,
    queryFn: ({ pageParam, signal }) =>
      pageDataSourceTables(
        dataSourceId,
        {
          databasePattern: filters.database || undefined,
          tablePattern: filters.table || undefined,
          offset: pageParam,
          limit: 100,
        },
        signal,
      ),
    getNextPageParam: (page) => page.nextOffset ?? undefined,
    enabled: Boolean(dataSourceId && currentUser?.id && currentWorkspace?.id),
    retry: false,
  });
  return { ...query, tables: query.data?.pages.flatMap((page) => page.items) ?? [] };
}
