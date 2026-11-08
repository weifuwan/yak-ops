import { useQuery } from "@tanstack/react-query";
import {
  getLatestDataSyncWorkflow,
  isWorkflowActive,
  listLatestDataSyncTaskInstances,
} from "@/service/data-sync";
import { detailQueryKeys } from "./query-keys";

/** Read the active Instance with user isolation and query-owned polling. */
export function useActiveTaskInstance(scope: string, taskId?: string, workflowCode?: string) {
  const query = useQuery({
    queryKey: detailQueryKeys.activeInstance(scope, taskId || ""),
    queryFn: ({ signal }) => listLatestDataSyncTaskInstances([taskId!], true, signal),
    enabled: Boolean(scope && taskId),
    refetchInterval: (result) => (result.state.data?.length ? 2000 : 15000),
  });
  const workflowQuery = useQuery({
    queryKey: detailQueryKeys.latestWorkflow(scope, workflowCode || ""),
    queryFn: ({ signal }) => getLatestDataSyncWorkflow(workflowCode!, signal),
    enabled: Boolean(scope && workflowCode),
    refetchInterval: (result) =>
      result.state.data && isWorkflowActive(result.state.data.state) ? 2000 : 15000,
  });
  return {
    workflow: workflowQuery.data,
    activeInstance: query.data?.find((instance) => instance.definitionId === taskId),
    ready: Boolean(scope && taskId && workflowCode && query.isSuccess && workflowQuery.isSuccess &&
      !query.isFetching && !workflowQuery.isFetching),
    refresh: async () => {
      await Promise.all([query.refetch(), workflowQuery.refetch()]);
    },
  };
}
