import { useEffect, useState } from "react";
import { toast } from "@yak-ops/yak-ui";
import {
  getCancelableDataSyncWorkflowInstanceId,
  isTaskInstanceActive,
} from "@/service/data-sync/execution";
import {
  cancelDataSyncInstance,
  getLatestDataSyncWorkflow,
  isWorkflowActive,
  runDataSyncTask,
  type DataSyncTaskRecord,
  type DataSyncWorkflowInstance,
} from "@/service/data-sync";
import { listLatestDataSyncTaskInstances } from "@/service/data-sync";
import type { TaskInstanceRecord } from "@/service/task-execution/types";

interface ActionsInput {
  taskId?: string;
  task?: DataSyncTaskRecord;
  canStart: boolean;
  activeInstance?: TaskInstanceRecord;
  workflow?: DataSyncWorkflowInstance;
  refreshActiveInstances: () => Promise<void>;
  refreshAfterCancel: () => Promise<void>;
  selectSubmitted: (id: string) => void;
  selectWorkflowSubmitted: (id: number) => void;
}
export function useExecutionActions({
  taskId,
  task,
  canStart,
  activeInstance,
  workflow,
  refreshActiveInstances,
  refreshAfterCancel,
  selectSubmitted,
  selectWorkflowSubmitted,
}: ActionsInput) {
  const [running, setRunning] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [pendingWorkflowInstanceId, setPendingWorkflowInstanceId] = useState<number>();
  useEffect(() => {
    if (pendingWorkflowInstanceId &&
        (activeInstance?.workflowInstanceId === pendingWorkflowInstanceId ||
          (workflow && workflow.id >= pendingWorkflowInstanceId))) {
      setPendingWorkflowInstanceId(undefined);
    }
  }, [activeInstance?.workflowInstanceId, workflow, pendingWorkflowInstanceId]);
  const run = async () => {
    if (!taskId || running || pendingWorkflowInstanceId || !canStart || task?.configurationError || task?.status !== "ONLINE")
      return;
    setRunning(true);
    try {
      const [latestInstances, latestWorkflow] = await Promise.all([
        listLatestDataSyncTaskInstances([taskId], false),
        getLatestDataSyncWorkflow(task!.workflowCode),
      ]);
      if (latestInstances.some((instance) => isTaskInstanceActive(instance.status)) ||
          (latestWorkflow && isWorkflowActive(latestWorkflow.state))) {
        toast.error("任务正在执行，请先停止");
        await refreshActiveInstances();
        return;
      }
      const submitted = await runDataSyncTask(task!.workflowCode);
      setPendingWorkflowInstanceId(submitted.workflowInstanceId);
      selectWorkflowSubmitted(submitted.workflowInstanceId);
      toast.success("运行请求已提交");
      await refreshActiveInstances();
      const latest = await listLatestDataSyncTaskInstances([taskId], false);
      const launched = latest.find(
        (instance) =>
          instance.workflowInstanceId === submitted.workflowInstanceId &&
          isTaskInstanceActive(instance.status),
      );
      if (launched) {
        selectSubmitted(launched.id);
        setPendingWorkflowInstanceId(undefined);
      }
    } finally {
      setRunning(false);
    }
  };
  const cancel = async () => {
    const workflowId = getCancelableDataSyncWorkflowInstanceId(workflow, activeInstance);
    if (!workflowId || canceling) return;
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
