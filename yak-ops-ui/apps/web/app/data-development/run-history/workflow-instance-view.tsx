import { useEffect, useState } from "react";
import { Download, RefreshCw, Square } from "lucide-react";
import { Button } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";

import {
  getWorkflowInstanceDetail,
  getWorkflowInstanceTasks,
  type WorkflowInstanceTask,
} from "@/service/workflow/management";
import { downloadTaskInstanceLog, getTaskInstanceLog } from "@/service/workflow/log";
import { stopWorkflowInstance } from "@/service/workflow/execution";
import { TaskTypeBadgeIcon } from "../workflow-editor/components/task-type-badge-icon";

export function WorkflowInstanceView({ instanceId }: { instanceId: number }) {
  const [selectedTask, setSelectedTask] = useState<WorkflowInstanceTask | null>(null);
  const [log, setLog] = useState("");
  const [stopping, setStopping] = useState(false);

  const detailQuery = useQuery({
    queryKey: ["data-development", "workflow-instance", instanceId],
    queryFn: ({ signal }) => getWorkflowInstanceDetail(instanceId, signal),
    refetchInterval: 5000,
  });
  const tasksQuery = useQuery({
    queryKey: ["data-development", "workflow-instance-tasks", instanceId],
    queryFn: ({ signal }) => getWorkflowInstanceTasks(instanceId, signal),
    refetchInterval: 5000,
  });

  useEffect(() => {
    if (!selectedTask && tasksQuery.data?.taskList?.length) {
      setSelectedTask(tasksQuery.data.taskList[0]);
    }
  }, [selectedTask, tasksQuery.data?.taskList]);

  const loadLog = async (task: WorkflowInstanceTask) => {
    setSelectedTask(task);
    try {
      const result = await getTaskInstanceLog(task.id);
      setLog(result.message || "");
    } catch {
      setLog("");
    }
  };

  const stop = async () => {
    setStopping(true);
    try {
      await stopWorkflowInstance(instanceId);
      await detailQuery.refetch();
      await tasksQuery.refetch();
    } finally {
      setStopping(false);
    }
  };

  const download = async () => {
    if (!selectedTask) return;
    const blob = await downloadTaskInstanceLog(selectedTask.id);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "task-" + selectedTask.id + ".log";
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const detail = detailQuery.data;
  const tasks = tasksQuery.data?.taskList ?? [];
  const canStop = detail && ["RUNNING_EXECUTION", "READY_PAUSE", "READY_STOP", "SUBMITTED_SUCCESS"].includes(detail.state);

  if (detailQuery.isLoading) {
    return <div className="flex h-full items-center justify-center text-[12px] text-[#8b919b]">加载运行实例...</div>;
  }

  return (
    <div className="flex h-full min-h-0 min-w-0 flex-1 flex-col bg-white">
      <div className="flex h-12 shrink-0 items-center justify-between border-b border-[#ececec] px-4">
        <div>
          <div className="text-[14px] font-medium text-[#24272c]">{detail?.name || "运行实例"}</div>
          <div className="mt-0.5 text-[11px] text-[#8b919b]">
            #{instanceId} · {detail?.state || "-"} · {detail?.duration || "-"}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button size="sm" variant="secondary" onClick={() => detailQuery.refetch()}>
            <RefreshCw size={13} />
            刷新
          </Button>
          {canStop ? (
            <Button size="sm" variant="secondary" onClick={stop} isDisabled={stopping}>
              <Square size={12} fill="currentColor" />
              {stopping ? "停止中..." : "停止"}
            </Button>
          ) : null}
        </div>
      </div>

      <div className="grid grid-cols-4 gap-px border-b border-[#ececec] bg-[#ececec]">
        {[
          ["执行人", detail?.executorName],
          ["Worker Group", detail?.workerGroup],
          ["Host", detail?.host],
          ["开始时间", detail?.startTime],
        ].map(([label, value]) => (
          <div key={label} className="bg-white px-4 py-2.5">
            <div className="text-[11px] text-[#8b919b]">{label}</div>
            <div className="mt-1 truncate text-[12px] text-[#30343a]">{value || "-"}</div>
          </div>
        ))}
      </div>

      <div className="min-h-0 flex-1 grid grid-cols-[minmax(260px,34%)_1fr]">
        <div className="min-h-0 overflow-y-auto border-r border-[#ececec]">
          <div className="px-3 py-2 text-[11px] font-medium text-[#7a818c]">节点实例</div>
          {tasks.map((task) => (
            <button
              key={task.id}
              type="button"
              onClick={() => loadLog(task)}
              className={[
                "flex w-full cursor-pointer items-center gap-2 border-b border-[#f1f2f4] px-3 py-2 text-left hover:bg-[#f7f8fa]",
                selectedTask?.id === task.id ? "bg-[#eef3ff]" : "",
              ].join(" ")}
            >
              <TaskTypeBadgeIcon taskType={task.taskType} size={16} />
              <span className="min-w-0 flex-1 truncate text-[12px] text-[#30343a]">{task.name}</span>
              <span className="text-[11px] text-[#8b919b]">{task.state}</span>
            </button>
          ))}
        </div>

        <div className="flex min-h-0 flex-col">
          <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#ececec] px-3">
            <span className="text-[12px] font-medium text-[#30343a]">
              {selectedTask?.name || "任务日志"}
            </span>
            <Button size="sm" variant="ghost" onClick={download} isDisabled={!selectedTask}>
              <Download size={13} />
              下载
            </Button>
          </div>
          <pre className="min-h-0 flex-1 overflow-auto bg-[#111315] p-3 text-[11px] leading-5 text-[#e7e9ed]">
            {log || "选择节点后查看日志"}
          </pre>
        </div>
      </div>
    </div>
  );
}
