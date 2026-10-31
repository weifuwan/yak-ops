import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { listDataSyncSolutions } from "@/service/data-sync";

/** Discovery is connection-independent and scoped to the active authenticated workspace. */
export function useSolutions(sourceType?: string, targetType?: string) {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const enabled = Boolean(currentUser?.id && currentWorkspace?.id && sourceType && targetType);
  const query = useQuery({
    queryKey: [
      "data-sync-solutions",
      currentUser?.id,
      currentWorkspace?.id,
      sourceType,
      targetType,
    ],
    queryFn: ({ signal }) => listDataSyncSolutions(sourceType!, targetType!, signal),
    enabled,
  });
  return {
    choices: query.data ?? [],
    loading: enabled && query.isPending,
    error: query.isError ? "同步方案加载失败，请重试" : "",
    refresh: () => void query.refetch(),
  };
}
