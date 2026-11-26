import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "@yak-ops/yak-ui";

import {
  deleteDataSyncTask,
  listLatestDataSyncTaskInstances,
  operateDataSyncInstance,
  publishDataSyncTask,
  runDataSyncTask,
  unpublishDataSyncTask,
  type DataSyncTaskRecord,
} from "@/service/data-sync";
import type { StartWorkflowOptions } from "@/service/workflow/execution";
import { listQueryKeys } from "./query-keys";

export type TaskCommand =
  | { kind: "run"; task: DataSyncTaskRecord; options?: StartWorkflowOptions }
  | { kind: "publish" | "unpublish" | "delete"; task: DataSyncTaskRecord }
  | { kind: "stop"; task: DataSyncTaskRecord; workflowInstanceId: number };

export function useTaskListActions(scope: string) {
  const queryClient = useQueryClient();
  const [submittedByTask, setSubmittedByTask] = useState<Record<string, number>>({});
  const mutation = useMutation({
    mutationFn: async (command: TaskCommand) => {
      switch (command.kind) {
        case "run":
          return runDataSyncTask(command.task.workflowCode, command.options);
        case "stop": {
          const instances = await listLatestDataSyncTaskInstances([command.task.id], false);
          const instance = instances.find(
            (item) => item.workflowInstanceId === command.workflowInstanceId,
          );
          if (!instance) throw new Error("未找到可操作的数据同步实例");
          await operateDataSyncInstance(instance.id, "STOP");
          break;
        }
        case "publish":
          await publishDataSyncTask(command.task.id, {
            version: command.task.definitionVersion,
            expectedRevision: command.task.revision,
          });
          break;
        case "unpublish":
          await unpublishDataSyncTask(command.task.id);
          break;
        case "delete":
          await deleteDataSyncTask(command.task.id);
          break;
      }
    },
    onSuccess: async (result, command) => {
      if (command.kind === "run" && result) {
        setSubmittedByTask((previous) => ({
          ...previous,
          [command.task.id]: result.workflowInstanceId,
        }));
      }
      const messages = {
        run: "运行请求已提交",
        stop: command.task.syncType === "REALTIME"
          ? "实时任务已停止，恢复点已保存"
          : "已发送停止请求",
        publish: "同步任务已发布",
        unpublish: "同步任务已下线",
        delete: "同步任务已删除",
      };
      toast.success(messages[command.kind]);

      if (command.kind === "run" || command.kind === "stop") {
        await queryClient.refetchQueries(
          { queryKey: listQueryKeys.instances(scope), type: "active" },
          { cancelRefetch: false },
        );
        return;
      }

      await queryClient.refetchQueries(
        { queryKey: listQueryKeys.tasks(scope), type: "active" },
        { cancelRefetch: false },
      );
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : "实例操作失败，请刷新状态后重试");
    },
  });
  const actionKey =
    mutation.isPending && mutation.variables
      ? mutation.variables.task.id + ":" + mutation.variables.kind
      : undefined;
  return { actionKey, pending: mutation.isPending, submittedByTask, execute: mutation.mutate };
}
