import { AlertCircle, Loader2, Wrench } from "lucide-react";

import type { ChatItem } from "./useAgentChat";

const TOOL_LABELS: Record<string, string> = {
  query_data_sync_instances: "查询同步实例",
  query_task_log: "读取任务日志",
  query_data_sync_tasks: "查询同步任务",
  query_data_sync_overview: "查询同步概览",
};

function toolLabel(tool?: string) {
  if (!tool) {
    return "工具调用";
  }
  return TOOL_LABELS[tool] ?? tool;
}

export function ChatMessageList({ items }: { items: ChatItem[] }) {
  return (
    <div className="space-y-3">
      {items.map((item) => {
        if (item.kind === "user") {
          return (
            <div key={item.id} className="flex justify-end">
              <div className="max-w-[85%] whitespace-pre-wrap rounded-xl rounded-br-sm bg-[#185FA5] px-3 py-2 text-[13px] leading-relaxed text-white">
                {item.content}
              </div>
            </div>
          );
        }

        if (item.kind === "assistant") {
          return (
            <div key={item.id} className="flex justify-start">
              <div className="max-w-[92%] whitespace-pre-wrap rounded-xl rounded-bl-sm border border-[#e8ebef] bg-[#f7f8fa] px-3 py-2 text-[13px] leading-relaxed text-[#1f2329]">
                {item.content}
              </div>
            </div>
          );
        }

        if (item.kind === "tool") {
          return (
            <div
              key={item.id}
              className="flex items-center gap-2 rounded-lg border border-[#e8ebef] bg-white px-3 py-1.5 text-[12px] text-[#5c6270]"
            >
              <Wrench className="h-3.5 w-3.5 shrink-0 text-[#8a8f99]" />
              <span className="min-w-0 flex-1 truncate">{toolLabel(item.tool)}</span>
              {item.running ? (
                <span className="flex items-center gap-1 text-[#185FA5]">
                  <Loader2 className="h-3 w-3 animate-spin" />
                  执行中
                </span>
              ) : (
                <span className="text-[#98a1ab]">{item.durationMs ?? 0}ms</span>
              )}
            </div>
          );
        }

        if (item.kind === "error") {
          return (
            <div
              key={item.id}
              className="flex items-start gap-2 rounded-lg border border-[#f3d6d6] bg-[#fdf3f3] px-3 py-2 text-[12px] leading-relaxed text-[#a32d2d]"
            >
              <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
              <span className="min-w-0 break-words">{item.content}</span>
            </div>
          );
        }

        return (
          <div key={item.id} className="flex items-center gap-2 text-[12px] text-[#8a8f99]">
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            {item.content}
          </div>
        );
      })}
    </div>
  );
}
