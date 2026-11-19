import { Sparkles } from "lucide-react";

import AppShell from "../layout/AppShell";

const DATA_AGENT_PRODUCT_LABEL = "Data Agent";

export function DataAgentPage() {
  return (
    <AppShell
      productLabel={DATA_AGENT_PRODUCT_LABEL}
      productPath="/data-agent"
    >
      <main className="flex min-h-0 min-w-0 flex-1 items-center justify-center overflow-y-auto bg-[#f6f6f6] px-6 py-12">
        <section
          aria-labelledby="data-agent-title"
          className="flex max-w-xl flex-col items-center text-center"
        >
          <div className="mb-6 flex size-14 items-center justify-center rounded-2xl bg-white text-[#fe2c55] shadow-sm ring-1 ring-black/5">
            <Sparkles size={26} strokeWidth={1.6} aria-hidden="true" />
          </div>
          <h1
            id="data-agent-title"
            className="text-[24px] font-semibold tracking-tight text-[#161823]"
          >
            你好，我是 Yak-OPS Data Agent
          </h1>
          <p className="mt-3 text-sm leading-7 text-[#666666]">
            我可以帮助你理解数据、构建任务，并协助分析数据开发中的问题。
          </p>
          <p className="mt-1 text-sm leading-7 text-[#8a8a8a]">
            智能对话与工具调用能力正在建设中，后续会逐步开放。
          </p>
        </section>
      </main>
    </AppShell>
  );
}

export default DataAgentPage;
