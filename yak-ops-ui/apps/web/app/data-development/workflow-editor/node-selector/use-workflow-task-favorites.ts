import { useMemo } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

import { useAuth } from "@/hooks/use-auth";
import {
  addWorkflowTaskFavorite,
  listWorkflowTaskFavorites,
  removeWorkflowTaskFavorite,
  type WorkflowTaskFavoriteRecord,
} from "@/service/workflow/favourite";

export const workflowTaskFavoriteQueryKey = (userId: string) =>
  ["workflow-task-favorites", userId] as const;

function isFavoriteRecord(record: WorkflowTaskFavoriteRecord): boolean {
  return Boolean(record.isCollection ?? record.collection);
}

function updateFavoriteRecords(
  records: WorkflowTaskFavoriteRecord[],
  taskType: string,
  taskCategory: string,
  favorite: boolean,
): WorkflowTaskFavoriteRecord[] {
  const existing = records.find((record) => record.taskType === taskType);
  if (!existing) {
    return favorite
      ? [...records, { taskType, taskCategory, isCollection: true }]
      : records;
  }

  return records.map((record) =>
    record.taskType === taskType
      ? { ...record, taskCategory, isCollection: favorite, collection: favorite }
      : record,
  );
}

/** Keeps task-type favorites shared between the sidebar and floating node pickers. */
export function useWorkflowTaskFavorites(open: boolean) {
  const { currentUser } = useAuth();
  const userId = currentUser?.id ?? "";
  const queryClient = useQueryClient();
  const queryKey = workflowTaskFavoriteQueryKey(userId);

  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) => listWorkflowTaskFavorites(signal),
    enabled: open && Boolean(userId),
    retry: false,
    staleTime: 30000,
    refetchOnWindowFocus: false,
  });

  const records = query.data ?? [];
  const favoriteTaskTypes = useMemo(
    () => new Set(records.filter(isFavoriteRecord).map((record) => record.taskType)),
    [records],
  );

  const mutation = useMutation({
    mutationKey: ["workflow-task-favorite", userId],
    mutationFn: async ({
      taskType,
      favorite,
    }: {
      taskType: string;
      taskCategory: string;
      favorite: boolean;
    }) => {
      const saved = favorite
        ? await addWorkflowTaskFavorite(taskType)
        : await removeWorkflowTaskFavorite(taskType);
      if (!saved) {
        throw new Error(favorite ? "服务端未确认收藏操作" : "服务端未确认取消收藏");
      }
    },
    onMutate: async ({ taskType, taskCategory, favorite }) => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData<WorkflowTaskFavoriteRecord[]>(queryKey);
      queryClient.setQueryData<WorkflowTaskFavoriteRecord[]>(
        queryKey,
        updateFavoriteRecords(previous ?? [], taskType, taskCategory, favorite),
      );
      return { previous };
    },
    onError: (error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(queryKey, context.previous);
      } else {
        queryClient.removeQueries({ queryKey, exact: true });
      }
      toast.error("收藏操作失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
    },
    onSuccess: (_result, variables) => {
      toast.success(variables.favorite ? "已添加到我的收藏" : "已取消收藏");
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey });
    },
  });

  const toggleFavorite = (taskType: string, taskCategory: string) => {
    if (!userId) {
      toast.warning("登录后才能收藏节点");
      return;
    }
    if (query.isPending || mutation.isPending) return;

    mutation.mutate({
      taskType,
      taskCategory,
      favorite: !favoriteTaskTypes.has(taskType),
    });
  };

  return {
    favoriteTaskTypes,
    loading: query.isPending && open && Boolean(userId),
    error: query.isError,
    retry: () => {
      void query.refetch();
    },
    mutating: mutation.isPending,
    canToggle: Boolean(userId),
    toggleFavorite,
  };
}
