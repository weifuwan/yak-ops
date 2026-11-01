import { useQueries } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { listDataSourceColumns, type DataSourceCatalogColumn } from "@/service/datasource";
import type { DataSyncTableMapping } from "@/service/data-sync";
import { tableKey } from "./table-catalog";

export function useReplicationMetadata(
  dataSourceId: string,
  mappings: DataSyncTableMapping[],
  enabled: boolean,
) {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const active = enabled && Boolean(dataSourceId && currentUser?.id && currentWorkspace?.id);
  const queries = useQueries({
    queries: (active ? mappings : []).map(({ source }) => ({
      queryKey: [
        "data-sync-columns",
        currentUser?.id,
        currentWorkspace?.id,
        dataSourceId,
        source.database,
        source.schema,
        source.table,
      ],
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        listDataSourceColumns(
          dataSourceId,
          {
            database: source.database,
            schema: source.schema,
            table: source.table,
          },
          signal,
        ),
    })),
  });
  return {
    columns: new Map(
      mappings.map((mapping, index) => [
        tableKey(mapping.source),
        queries[index]?.data as DataSourceCatalogColumn[] | undefined,
      ]),
    ),
    loading: active && queries.some((query) => query.isPending),
    error: queries.some((query) => query.isError) ? "来源字段加载失败，请重试" : "",
    refresh: () => {
      for (const query of queries) void query.refetch();
    },
  };
}
