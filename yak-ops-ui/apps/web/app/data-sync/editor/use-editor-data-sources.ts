import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { listDataSources, type DataSourceRecord } from "@/service/datasource";

export function useEditorDataSources(connections: DataSourceRecord[]) {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const enabled = Boolean(currentUser?.id && currentWorkspace?.id);
  const query = useQuery({
    queryKey: ["data-sync-editor-connections", currentUser?.id, currentWorkspace?.id],
    queryFn: ({ signal }) => listDataSources({ pageNo: 1, pageSize: 200 }, signal),
    enabled,
  });
  return {
    dataSources: [
      ...new Map(
        [...connections, ...(query.data?.bizData ?? [])].map((value) => [value.id, value]),
      ).values(),
    ],
    dataSourcesLoading: enabled && query.isPending,
    dataSourcesError: query.isError ? "数据源加载失败，请重试" : "",
    loadDataSources: () => void query.refetch(),
  };
}
