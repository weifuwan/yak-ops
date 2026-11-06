import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

import {
  cancelDataSyncInstance,
  deleteDataSyncTask,
  publishDataSyncTask,
  runDataSyncTask,
  unpublishDataSyncTask,
  type DataSyncTaskRecord,
} from "@/service/data-sync";
import type { TaskInstanceRecord } from "@/service/task-execution";
import { listQueryKeys } from "./query-keys";

export type TaskCommand =
  | { kind: "run" | "publish" | "unpublish" | "delete"; task: DataSyncTaskRecord }
  | { kind: "stop"; task: DataSyncTaskRecord; instance: TaskInstanceRecord };

export function useTaskListActions(scope: string) {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: async (command: TaskCommand) => {
      switch (command.kind) {
        case "run":
          await runDataSyncTask(command.task.id);
          break;
        case "stop":
          await cancelDataSyncInstance(command.instance.id);
          break;
        case "publish":
          await publishDataSyncTask(command.task.id, {
            version: command.task.definitionVersion,
            expectedRevision: command.task.revision,
            requestId: crypto.randomUUID(),
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
    onSuccess: async (_, command) => {
      const messages = {
        run: "运行请求已提交",
        stop: "已发送停止请求",
        publish: "同步任务已发布",
        unpublish: "同步任务已下线",
        delete: "同步任务已删除",
      };
      toast.success(messages[command.kind]);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: listQueryKeys.tasks(scope) }),
        queryClient.invalidateQueries({ queryKey: listQueryKeys.instances(scope) }),
      ]);
    },
  });
  const actionKey =
    mutation.isPending && mutation.variables
      ? mutation.variables.task.id + ":" + mutation.variables.kind
      : undefined;
  return { actionKey, pending: mutation.isPending, execute: mutation.mutate };
}
