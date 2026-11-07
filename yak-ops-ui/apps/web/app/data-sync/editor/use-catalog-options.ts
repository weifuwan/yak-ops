import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  pageDataSourceTables,
  type DataSourceCatalogTable,
} from "@/service/datasource";
export interface CatalogOptions {
  tables: DataSourceCatalogTable[];
  total: number;
  loading: boolean;
  error: string;
  refresh: () => void;
}

export function useCatalogOptions(
  dataSourceId: string,
  database: string,
  keyword: string,
  pageNo: number,
  pageSize: number,
  active = true,
): CatalogOptions {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const enabled = active && Boolean(
    currentUser?.id && currentWorkspace?.id && dataSourceId && database,
  );
  const query = useQuery({
    queryKey: [
      "data-sync-catalog",
      currentUser?.id,
      currentWorkspace?.id,
      dataSourceId,
      database,
      keyword,
      pageNo,
      pageSize,
    ],
    queryFn: ({ signal }) =>
      pageDataSourceTables(
        dataSourceId,
        {
          database,
          keyword: keyword || undefined,
          limit: pageSize,
          offset: (pageNo - 1) * pageSize,
        },
        signal,
      ),
    enabled,
  });
  return {
    tables: query.data?.items ?? [],
    total: query.data?.total ?? 0,
    loading: enabled && query.isPending,
    error: enabled && query.isError ? "表信息加载失败" : "",
    refresh: () => void query.refetch(),
  };
}
