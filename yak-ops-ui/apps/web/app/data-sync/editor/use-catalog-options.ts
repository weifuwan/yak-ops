import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  listDataSourceSchemas,
  pageDataSourceTables,
  type DataSourceCatalogTable,
} from "@/service/datasource";
import type { CatalogOptions } from "./types";

export function useCatalogOptions(
  dataSourceId: string,
  database: string,
  schema: string,
  active = true,
): CatalogOptions {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const enabled = active && Boolean(currentUser?.id && currentWorkspace?.id && dataSourceId);
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
        (async () => {
          const all: DataSourceCatalogTable[] = [];
          let offset: number | null = 0;
          while (offset !== null) {
            const page = await pageDataSourceTables(
              dataSourceId,
              { database: database || undefined, schema: schema || undefined, limit: 500, offset },
              signal,
            );
            all.push(...page.items);
            if (page.nextOffset !== null && page.nextOffset <= offset)
              throw new Error("目录分页响应无效");
            offset = page.nextOffset;
          }
          return all;
        })(),
      ]);
      return { schemas, tables };
    },
    enabled,
  });
  return {
    schemas: query.data?.schemas ?? [],
    tables: query.data?.tables ?? [],
    loading: enabled && query.isPending,
    error: enabled && query.isError ? "表信息加载失败" : "",
    refresh: () => void query.refetch(),
  };
}
