import { ChevronDown } from "lucide-react";

import { formatToolInput } from "../../../../utils/message-formatters";

type AgentActivityDetailsProps = {
  input?: string;
  output?: string;
};

export function AgentActivityDetails({ input, output }: AgentActivityDetailsProps) {
  if (!input && !output) return null;

  return (
    <details className="group mt-2 min-w-0 border-t border-[#edf0ef] pt-2">
      <summary className="flex cursor-pointer list-none items-center gap-1.5 text-[11px] text-[#87928f] transition-colors hover:text-[#46534f] [&::-webkit-details-marker]:hidden">
        <ChevronDown
          size={12}
          className="shrink-0 transition-transform duration-200 group-open:rotate-180"
        />
        查看调用详情
      </summary>
      <div className="mt-2 flex min-w-0 flex-col gap-2.5">
        {input ? (
          <section className="min-w-0">
            <div className="mb-1 text-[10px] font-medium tracking-wide text-[#87928f]">
              输入参数
            </div>
            <pre className="max-h-40 overflow-auto whitespace-pre-wrap break-all rounded-lg bg-[#f3f5f4] p-2.5 font-mono text-[11px] leading-5 text-[#596560]">
              {formatToolInput(input)}
            </pre>
          </section>
        ) : null}
        {output ? (
          <section className="min-w-0">
            <div className="mb-1 text-[10px] font-medium tracking-wide text-[#87928f]">
              执行结果
            </div>
            <pre className="max-h-48 overflow-auto whitespace-pre-wrap break-words rounded-lg bg-[#f3f5f4] p-2.5 font-mono text-[11px] leading-5 text-[#596560]">
              {output}
            </pre>
          </section>
        ) : null}
      </div>
    </details>
  );
}
