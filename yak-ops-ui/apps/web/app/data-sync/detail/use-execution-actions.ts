import { useState } from "react";
import { toast } from "@yak-ops/yak-ui";
import { canCancelTaskInstance } from "@/service/data-sync/execution";
import {
  cancelDataSyncInstance,
  runDataSyncTask,
  type DataSyncInstanceRecord,
  type DataSyncTaskRecord,
} from "@/service/data-sync";
import { listLatestDataSyncTaskInstances } from "@/service/data-sync";

interface ActionsInput {
  taskId?: string;
  task?: DataSyncTaskRecord;
  canStart: boolean;
  selectedInstance?: DataSyncInstanceRecord;
  refreshActiveInstances: () => Promise<void>;
  refreshAfterCancel: () => Promise<void>;
  selectSubmitted: (id: string) => void;
}
export function useExecutionActions({
  taskId,
  task,
  canStart,
  selectedInstance,
  refreshActiveInstances,
  refreshAfterCancel,
  selectSubmitted,
}: ActionsInput) {
  const [running, setRunning] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const run = async () => {
    if (!taskId || running || !canStart || task?.configurationError || task?.status !== "ONLINE")
      return;
    setRunning(true);
    try {
      const active = await listLatestDataSyncTaskInstances([taskId], true);
      if (active.length > 0) {
        toast.error("任务正在执行，请先停止");
        await refreshActiveInstances();
        return;
      }
      await runDataSyncTask(task!.workflowCode);
      toast.success("运行请求已提交");
      await refreshActiveInstances();
      const latest = await listLatestDataSyncTaskInstances([taskId], true);
      if (latest[0]) selectSubmitted(latest[0].id);
    } finally {
      setRunning(false);
    }
  };
  const cancel = async () => {
    if (!selectedInstance || !canCancelTaskInstance(selectedInstance.status) || canceling) return;
    setCanceling(true);
    try {
      if (!selectedInstance.workflowInstanceId) throw new Error("缺少工作流实例编号");
      await cancelDataSyncInstance(selectedInstance.workflowInstanceId);
      toast.success("已发送停止请求");
      await Promise.all([refreshAfterCancel(), refreshActiveInstances()]);
    } finally {
      setCanceling(false);
    }
  };
  const copyTaskId = async () => {
    if (!task) return;
    try {
      await navigator.clipboard.writeText(task.id);
      toast.success("已复制任务 ID");
    } catch {
      toast.error("复制失败");
    }
  };
  return { run, cancel, copyTaskId, running, canceling };
}
