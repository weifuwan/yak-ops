import { useEffect, useState } from "react";
import { toast } from "@yak-ops/yak-ui";
import { canCancelTaskInstance } from "@/service/data-sync/execution";
import {
  cancelDataSyncInstance,
  runDataSyncTask,
  type DataSyncInstanceRecord,
  type DataSyncTaskRecord,
} from "@/service/data-sync";
import { listLatestDataSyncTaskInstances } from "@/service/data-sync";
import type { TaskInstanceRecord } from "@/service/task-execution/types";

interface ActionsInput {
  taskId?: string;
  task?: DataSyncTaskRecord;
  canStart: boolean;
  selectedInstance?: DataSyncInstanceRecord;
  activeInstance?: TaskInstanceRecord;
  refreshActiveInstances: () => Promise<void>;
  refreshAfterCancel: () => Promise<void>;
  selectSubmitted: (id: string) => void;
}
export function useExecutionActions({
  taskId,
  task,
  canStart,
  selectedInstance,
  activeInstance,
  refreshActiveInstances,
  refreshAfterCancel,
  selectSubmitted,
}: ActionsInput) {
  const [running, setRunning] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [pendingWorkflowInstanceId, setPendingWorkflowInstanceId] = useState<number>();
  useEffect(() => {
    if (pendingWorkflowInstanceId && activeInstance?.workflowInstanceId === pendingWorkflowInstanceId) {
      setPendingWorkflowInstanceId(undefined);
    }
  }, [activeInstance?.workflowInstanceId, pendingWorkflowInstanceId]);
  const run = async () => {
    if (!taskId || running || pendingWorkflowInstanceId || !canStart || task?.configurationError || task?.status !== "ONLINE")
      return;
    setRunning(true);
    try {
      const active = await listLatestDataSyncTaskInstances([taskId], true);
      if (active.length > 0) {
        toast.error("任务正在执行，请先停止");
        await refreshActiveInstances();
        return;
      }
      const submitted = await runDataSyncTask(task!.workflowCode);
      setPendingWorkflowInstanceId(submitted.workflowInstanceId);
      toast.success("运行请求已提交");
      await refreshActiveInstances();
      const latest = await listLatestDataSyncTaskInstances([taskId], true);
      const launched = latest.find((instance) => instance.workflowInstanceId === submitted.workflowInstanceId);
      if (launched) {
        selectSubmitted(launched.id);
        setPendingWorkflowInstanceId(undefined);
      }
    } finally {
      setRunning(false);
    }
  };
  const cancel = async () => {
    const workflowId = pendingWorkflowInstanceId ?? selectedInstance?.workflowInstanceId;
    if (!workflowId || canceling) return;
    if (!pendingWorkflowInstanceId && (!selectedInstance || !canCancelTaskInstance(selectedInstance.status))) return;
    setCanceling(true);
    try {
      await cancelDataSyncInstance(workflowId);
      setPendingWorkflowInstanceId(undefined);
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
  return { run, cancel, copyTaskId, running, canceling, pendingWorkflowInstanceId };
}
