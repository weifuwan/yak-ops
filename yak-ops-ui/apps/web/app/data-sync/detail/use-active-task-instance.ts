import { useQuery } from "@tanstack/react-query";
import { listLatestTaskInstances } from "@/service/task-execution";
import { detailQueryKeys } from "./query-keys";

/** Read the active Instance with user isolation and query-owned polling. */
export function useActiveTaskInstance(scope: string, taskId?: string) {
  const query = useQuery({
    queryKey: detailQueryKeys.activeInstance(scope, taskId || ""),
    queryFn: ({ signal }) => listLatestTaskInstances([taskId!], true, signal),
    enabled: Boolean(scope && taskId),
    refetchInterval: (result) => (result.state.data?.length ? 2000 : 15000),
  });
  return {
    activeInstance: query.data?.find((instance) => instance.definitionId === taskId),
    ready: Boolean(scope && taskId && query.isSuccess && !query.isFetching),
    refresh: async () => {
      await query.refetch();
    },
  };
}
