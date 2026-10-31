import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { listDataSourceSchemas, listDataSourceTables } from "@/service/datasource";
import type { CatalogOptions } from "./types";

export function useCatalogOptions(
  dataSourceId: string,
  database: string,
  schema: string,
): CatalogOptions {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const enabled = Boolean(currentUser?.id && currentWorkspace?.id && dataSourceId);
  const query = useQuery({
    queryKey: [
      "data-sync-catalog",
      currentUser?.id,
      currentWorkspace?.id,
      dataSourceId,
      database,
      schema,
    ],
    queryFn: async ({ signal }) => {
      const [schemas, tables] = await Promise.all([
        listDataSourceSchemas(dataSourceId, database || undefined, signal),
        listDataSourceTables(
          dataSourceId,
          { database: database || undefined, schema: schema || undefined, limit: 500 },
          signal,
        ),
      ]);
      return { schemas, tables };
    },
    enabled,
  });
  return {
    schemas: query.data?.schemas ?? [],
    tables: query.data?.tables ?? [],
    loading: enabled && query.isPending,
    error: query.isError ? "表信息加载失败" : "",
    refresh: () => void query.refetch(),
  };
}
