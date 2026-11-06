import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { getDataSource } from "@/service/datasource";
import { getUsersByIds } from "@/service/user";
import { getDataSyncTask } from "@/service/data-sync";
import { detailQueryKeys } from "./query-keys";

export function useDetailScope() {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  return currentUser?.id && currentWorkspace?.id ? currentUser.id + ":" + currentWorkspace.id : "";
}

export function useTaskInformation(taskId: string | undefined, scope: string) {
  const navigate = useNavigate();
  const taskQuery = useQuery({
    queryKey: detailQueryKeys.task(scope, taskId || ""),
    queryFn: ({ signal }) => getDataSyncTask(taskId!, signal),
    enabled: Boolean(scope && taskId),
    retry: false,
  });
  const task = taskQuery.data;
  const sourceId = task?.sourceDataSourceId;
  const targetId = task?.targetDataSourceId;
  const sourcesQuery = useQuery({
    queryKey: detailQueryKeys.dataSources(scope, sourceId || "", targetId || ""),
    queryFn: async ({ signal }) => {
      const [source, target] = await Promise.all([
        getDataSource(sourceId!, signal).catch(() => undefined),
        getDataSource(targetId!, signal).catch(() => undefined),
      ]);
      return { source, target };
    },
    enabled: Boolean(scope && sourceId && targetId),
  });
  const userId = task?.updateBy;
  const userQuery = useQuery({
    queryKey: detailQueryKeys.updatedBy(scope, userId || ""),
    queryFn: async () => {
      const users = await getUsersByIds([userId!]);
      return users[0]?.realName || users[0]?.userName || "-";
    },
    enabled: Boolean(scope && userId && userId !== "system"),
  });
  useEffect(() => {
    if (taskQuery.isError && taskId) navigate("/data-sync", { replace: true });
  }, [taskId, taskQuery.isError, navigate]);

  return {
    task,
    taskLoading: taskQuery.isPending,
    dataSources: sourcesQuery.data ?? {},
    updatedBy: userId === "system" ? "SYSTEM" : (userQuery.data ?? "-"),
  };
}
