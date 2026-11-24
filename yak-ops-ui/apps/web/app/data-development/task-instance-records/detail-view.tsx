import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download, FileText, RefreshCw } from "lucide-react";
import { Button } from "@yak-ops/yak-ui";

import {
  getTaskInstanceDetail,
  type TaskInstanceRecord,
} from "@/service/workflow/task-instances";
import {
  downloadTaskInstanceLog,
  getTaskInstanceLog,
} from "@/service/workflow/log";

export function TaskInstanceDetailView({
  record,
}: {
  record: TaskInstanceRecord;
}) {
  const [log, setLog] = useState("");
  const [logLoading, setLogLoading] = useState(false);

  const detailQuery = useQuery({
    queryKey: ["data-development", "task-instance-detail", record.id],
    queryFn: ({ signal }) => getTaskInstanceDetail(record.id, signal),
  });

  const detail = detailQuery.data ?? record;

  const loadLog = async () => {
    setLogLoading(true);
    try {
      const result = await getTaskInstanceLog(record.id, 0, 1000);
      setLog(result.message || "");
    } catch (error) {
      setLog(error instanceof Error ? error.message : "日志加载失败");
    } finally {
      setLogLoading(false);
    }
  };

  const downloadLog = async () => {
    const blob = await downloadTaskInstanceLog(record.id);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `task-${record.id}.log`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const fields = [
    ["执行状态", detail.state],
    ["所属工作流", detail.workflowInstanceName],
    ["工作流实例", "#" + detail.workflowInstanceId],
    ["节点类型", detail.taskType],
    ["执行人", detail.executorName],
    ["Worker", detail.host],
    ["Worker Group", detail.workerGroup],
    ["开始时间", detail.startTime],
    ["结束时间", detail.endTime],
    ["运行耗时", detail.duration],
    ["重试次数", String(detail.retryTimes ?? 0)],
    ["任务定义", "#" + detail.taskCode],
  ];

  return (
    <div className="h-full min-h-0 w-full min-w-0 overflow-y-auto bg-[#f7f8fa] p-5">
      <div className="mx-auto flex w-full max-w-[1400px] flex-col gap-4">
        <section className="rounded-lg border border-[#e6e9ee] bg-white p-5">
          <div className="mb-4 flex items-start justify-between gap-4">
            <div>
              <div className="text-sm font-semibold text-[#30343a]">
                {detail.name || "任务 #" + detail.id}
              </div>
              <div className="mt-1 text-xs text-[#8a9099]">
                任务实例 #{detail.id}
              </div>
            </div>
            <Button
              size="sm"
              variant="secondary"
              onClick={() => void detailQuery.refetch()}
              isDisabled={detailQuery.isFetching}
            >
              <RefreshCw
                size={13}
                className={detailQuery.isFetching ? "animate-spin" : ""}
              />
              刷新
            </Button>
          </div>

          <div className="grid grid-cols-2 gap-3 xl:grid-cols-3 2xl:grid-cols-4 max-md:grid-cols-1">
            {fields.map(([label, value]) => (
              <div
                key={label}
                className="rounded-md border border-[#edf0f3] bg-[#fafbfc] px-3 py-2.5"
              >
                <div className="text-[10px] text-[#8a9099]">{label}</div>
                <div className="mt-1 break-all text-xs text-[#30343a]">
                  {value || "-"}
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="min-h-[280px] overflow-hidden rounded-lg border border-[#e6e9ee] bg-white">
          <div className="flex items-center justify-between border-b border-[#edf0f3] px-4 py-3">
            <div className="flex items-center gap-2 text-sm font-semibold text-[#30343a]">
              <FileText size={15} />
              执行日志
            </div>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="secondary"
                onClick={() => void loadLog()}
                isPending={logLoading}
              >
                {logLoading ? "加载中..." : "查看日志"}
              </Button>
              <Button size="sm" variant="secondary" onClick={() => void downloadLog()}>
                <Download size={13} />
                下载日志
              </Button>
            </div>
          </div>
          <pre className="m-0 min-h-[280px] max-h-[55vh] overflow-auto bg-[#111315] p-4 text-[11px] leading-5 text-[#e7e9ed]">
            {log || "点击“查看日志”加载任务执行日志"}
          </pre>
        </section>
      </div>
    </div>
  );
}
