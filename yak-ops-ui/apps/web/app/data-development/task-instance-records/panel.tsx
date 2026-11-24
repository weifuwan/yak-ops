import { useState } from "react";
import { ListChecks, Search } from "lucide-react";
import {
  Input,
  PanelHeader,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";

import { useWorkspace } from "@/hooks/use-workspace";
import {
  listTaskInstances,
  type TaskInstanceExecuteType,
  type TaskInstanceRecord,
} from "@/service/workflow/task-instances";
import { useDataDevelopmentWorkbench } from "../workbench-context";

const TYPE_ITEMS: Record<TaskInstanceExecuteType, string> = {
  BATCH: "离线任务",
  STREAM: "实时任务",
};

export function TaskInstanceRecordsPanel({ width }: { width: number }) {
  const { currentWorkspace } = useWorkspace();
  const [keyword, setKeyword] = useState("");
  const [executeType, setExecuteType] =
    useState<TaskInstanceExecuteType>("BATCH");
  const { activeTabId, openTaskInstanceRecord } = useDataDevelopmentWorkbench();

  const query = useQuery({
    queryKey: [
      "data-development",
      "task-instance-panel",
      currentWorkspace?.id,
      executeType,
      keyword,
    ],
    queryFn: ({ signal }) =>
      listTaskInstances(
        {
          pageNo: 1,
          pageSize: 100,
          taskExecuteType: executeType,
          searchVal: keyword.trim() || undefined,
        },
        signal,
      ),
    enabled: Boolean(currentWorkspace?.id),
    refetchInterval: 8000,
  });

  const records = query.data?.totalList ?? [];

  return (
    <aside
      aria-label="任务运行记录"
      className="flex h-full shrink-0 flex-col bg-[var(--yak-components-panel-bg)]"
      style={{ width }}
    >
      <PanelHeader title="任务运行记录" />
      <div className="space-y-2 border-b border-[#ececec] px-3 py-2">
        <div className="relative">
          <Search
            size={14}
            className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-input-icon)]"
          />
          <Input
            size="small"
            variant="outlined"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索任务 / 工作流"
            className="h-7 pl-8"
          />
        </div>
        <Select
          size="small"
          items={TYPE_ITEMS}
          value={executeType}
          onValueChange={(value) =>
            setExecuteType((value ?? "BATCH") as TaskInstanceExecuteType)
          }
        >
          <SelectTrigger variant="outlined" className="h-7">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(TYPE_ITEMS).map(([value, label]) => (
              <SelectItem key={value} value={value as TaskInstanceExecuteType}>
                <SelectItemText>{label}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-2 py-2">
        {query.isLoading ? (
          <div className="flex h-24 items-center justify-center text-xs text-[#9aa0a8]">
            加载中...
          </div>
        ) : records.length === 0 ? (
          <div className="px-3 py-10 text-center text-xs text-[#9aa0a8]">
            暂无任务运行记录
          </div>
        ) : (
          records.map((record: TaskInstanceRecord) => {
            const active = activeTabId === "task-instance:" + record.id;
            return (
              <button
                key={record.id}
                type="button"
                onClick={() => openTaskInstanceRecord(record)}
                className={[
                  "mb-0.5 flex w-full cursor-pointer items-start gap-2 rounded-md px-2 py-2 text-left transition-colors",
                  active
                    ? "bg-[#dfe7ff] text-[#20201e]"
                    : "text-[#666] hover:bg-[#e9e9e9] hover:text-[#20201e]",
                ].join(" ")}
              >
                <ListChecks size={14} className="mt-0.5 shrink-0 text-[#7f8ba1]" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-xs font-medium">
                    {record.name || "任务 #" + record.id}
                  </span>
                  <span className="mt-0.5 block truncate text-[10px] text-[#9aa0a8]">
                    {record.state || "-"} · 实例 #{record.id}
                  </span>
                </span>
              </button>
            );
          })
        )}
      </div>
    </aside>
  );
}
