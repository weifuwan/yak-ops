import { useState } from "react";
import { History, Search } from "lucide-react";
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

import {
  listWorkflowInstances,
  type WorkflowInstanceSummary,
} from "@/service/workflow/management";
import { useDataDevelopmentWorkbench } from "../workbench-context";

const STATE_OPTIONS = [
  { value: "ALL", label: "全部状态" },
  { value: "RUNNING_EXECUTION", label: "运行中" },
  { value: "SUCCESS", label: "成功" },
  { value: "FAILURE", label: "失败" },
  { value: "STOP", label: "已停止" },
];

function stateLabel(state?: string | null) {
  return STATE_OPTIONS.find((item) => item.value === state)?.label ?? state ?? "-";
}

export function RunHistoryPanel({ width }: { width: number }) {
  const [keyword, setKeyword] = useState("");
  const [state, setState] = useState("ALL");
  const { openWorkflowInstance } = useDataDevelopmentWorkbench();

  const query = useQuery({
    queryKey: ["data-development", "run-history", keyword, state],
    queryFn: ({ signal }) =>
      listWorkflowInstances(
        undefined,
        1,
        100,
        signal,
        { searchVal: keyword.trim() || undefined, stateType: state === "ALL" ? undefined : state },
      ),
    refetchInterval: 5000,
  });

  const items: WorkflowInstanceSummary[] = query.data?.totalList ?? [];

  return (
    <aside
      aria-label="运行历史"
      className="flex h-full shrink-0 flex-col bg-[var(--yak-components-panel-bg)]"
      style={{ width }}
    >
      <PanelHeader title="运行历史" />
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
            placeholder="工作流 / 实例"
            className="h-6 pl-8"
          />
        </div>
        <Select
          size="small"
          items={Object.fromEntries(STATE_OPTIONS.map((option) => [option.value, option.label]))}
          value={state}
          onValueChange={(value) => setState(String(value ?? "ALL"))}
        >
          <SelectTrigger variant="outlined" className="h-6">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {STATE_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                <SelectItemText>{option.label}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {query.isLoading ? (
          <div className="flex h-24 items-center justify-center text-[12px] text-[#9aa0a8]">加载中...</div>
        ) : items.length === 0 ? (
          <div className="px-4 py-10 text-center text-[12px] text-[#9aa0a8]">暂无运行记录</div>
        ) : (
          items.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => openWorkflowInstance(item.id, (item.name || "工作流") + " · 运行 #" + item.id)}
              className="group flex w-full cursor-pointer items-start gap-2 border-b border-[#f0f1f3] px-3 py-2.5 text-left hover:bg-[#f7f8fa]"
            >
              <History size={14} className="mt-0.5 shrink-0 text-[#7a8290]" />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[12px] font-medium text-[#30343a]">
                  {item.name || "未命名工作流"}
                </span>
                <span className="mt-0.5 block truncate text-[11px] text-[#8a909a]">
                  {stateLabel(item.state)} · {item.startTime || "-"}
                </span>
              </span>
            </button>
          ))
        )}
      </div>
    </aside>
  );
}
