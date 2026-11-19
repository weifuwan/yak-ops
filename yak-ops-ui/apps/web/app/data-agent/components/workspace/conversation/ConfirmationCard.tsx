import { ShieldCheck } from "lucide-react";

import { TOOL_LABELS } from "../../../model/constants";
import type { PendingConfirmation } from "../../../model/types";
import { formatToolInput } from "../../../utils/message-formatters";

type ConfirmationCardProps = {
  event: PendingConfirmation["event"];
  onResolve: (approved: boolean) => void;
};

export function ConfirmationCard({
  event,
  onResolve,
}: ConfirmationCardProps) {
  return (
    <div className="mt-3 rounded-[14px] border border-[#ead9b7] bg-[#fffaf2] p-3.5">
      <div className="flex items-center gap-2 text-[13px] font-semibold text-[#745b32]">
        <ShieldCheck size={16} />
        需要你确认后才能执行
      </div>
      <p className="mb-3 mt-1 text-xs leading-5 text-[#927b56]">
        以下操作可能修改 Yak-OPS 中的数据。请检查操作内容后再决定是否继续。
      </p>
      <div className="flex flex-col gap-2">
        {event.toolCalls.map((toolCall) => (
          <div
            key={toolCall.id}
            className="rounded-lg border border-[#eee2cc] bg-white/80 p-2.5"
          >
            <div className="text-xs font-medium text-[#51442f]">
              {TOOL_LABELS[toolCall.name] ?? toolCall.name}
            </div>
            {toolCall.input ? (
              <pre className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap break-all rounded-md bg-[#faf7f1] p-2 text-[11px] leading-5 text-[#756b5a]">
                {formatToolInput(toolCall.input)}
              </pre>
            ) : null}
          </div>
        ))}
      </div>
      <div className="mt-3 flex justify-end gap-2">
        <button
          type="button"
          className="h-8 cursor-pointer rounded-lg border border-[#e4d9c8] bg-white px-3 text-xs font-medium text-[#756b5a] transition-colors hover:bg-[#f8f4ed]"
          onClick={() => onResolve(false)}
        >
          拒绝
        </button>
        <button
          type="button"
          className="h-8 cursor-pointer rounded-lg bg-[#20201e] px-3 text-xs font-medium text-white transition-colors hover:bg-[#454542]"
          onClick={() => onResolve(true)}
        >
          确认执行
        </button>
      </div>
    </div>
  );
}
