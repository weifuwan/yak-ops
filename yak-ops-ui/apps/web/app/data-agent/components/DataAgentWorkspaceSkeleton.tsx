const SIDEBAR_ROWS = ["w-10", "w-12", "w-[76px]", "w-[68px]"] as const;
const RECENT_ROWS = ["w-28", "w-20", "w-32"] as const;
const QUICK_ACTIONS = [0, 1, 2, 3] as const;

export function DataAgentWorkspaceSkeleton() {
  return (
    <div
      role="status"
      aria-label="正在加载 Data Agent"
      aria-busy="true"
      className="flex min-h-0 min-w-0 flex-1 overflow-hidden bg-[#f0f0f0] text-[#20201e]"
    >
      <span className="sr-only">正在恢复会话和工作空间…</span>

      <aside
        aria-hidden="true"
        className="flex h-full w-[256px] shrink-0 flex-col bg-[#f0f0f0] px-2 pb-2 pt-3 max-md:hidden"
      >
        <div className="flex h-6 shrink-0 items-center px-2">
          <span className="data-agent-skeleton size-4 rounded-[5px]" />
        </div>

        <div className="mt-2 flex h-8 shrink-0 items-center gap-2 rounded-[10px] border border-[#e1e4e4] bg-[#f5f5f5] px-1.5">
          <span className="data-agent-skeleton size-5 rounded-[5px]" />
          <span className="data-agent-skeleton h-3 w-24 rounded-full" />
          <span className="data-agent-skeleton ml-auto size-3 rounded-full" />
        </div>

        <div className="mt-2 flex shrink-0 flex-col gap-0.5">
          {SIDEBAR_ROWS.map((width, index) => (
            <div key={index} className="flex h-8 items-center gap-2 rounded-lg px-2">
              <span className="data-agent-skeleton size-4 rounded-[4px]" />
              <span className={`data-agent-skeleton h-3 rounded-full ${width}`} />
            </div>
          ))}
        </div>

        <div className="mt-3 border-t border-[#dedede] pt-3">
          <span className="data-agent-skeleton mb-2 ml-2 h-3 w-20 rounded-full" />
          <div className="flex flex-col gap-1">
            {RECENT_ROWS.map((width, index) => (
              <div key={index} className="flex h-8 items-center gap-2 px-2">
                <span className="data-agent-skeleton size-[13px] rounded-[4px]" />
                <span className={`data-agent-skeleton h-3 rounded-full ${width}`} />
              </div>
            ))}
          </div>
        </div>

        <div className="mt-auto shrink-0 px-1">
          <div className="flex h-[58px] items-center justify-between rounded-[14px] border border-[#ddddda] bg-[#fafafa] px-3">
            <div className="flex min-w-0 flex-col gap-2">
              <span className="data-agent-skeleton h-3 w-20 rounded-full" />
              <span className="data-agent-skeleton h-2.5 w-28 rounded-full" />
            </div>
            <span className="data-agent-skeleton size-8 shrink-0 rounded-full" />
          </div>
          <div className="flex h-8 items-center px-1.5 pt-2">
            <span className="data-agent-skeleton size-5 rounded-full" />
          </div>
        </div>
      </aside>

      <main className="flex min-h-0 min-w-0 flex-1 bg-[#f0f0f0] p-2 max-md:p-0">
        <section className="relative flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-[#e7e7e4] bg-white max-md:rounded-none max-md:border-0">
          <div className="pointer-events-none absolute inset-x-0 top-0 z-30 flex items-center justify-between px-[7px] pt-2">
            <span className="data-agent-skeleton size-7 rounded-lg" />
            <span className="data-agent-skeleton size-7 rounded-lg" />
          </div>

          <div className="flex min-h-0 flex-1 items-center justify-center overflow-y-auto px-6 py-10">
            <div className="w-full max-w-[540px]">
              <div className="rounded-[14px] border border-[#e1e5e5] bg-white p-3">
                <span className="data-agent-skeleton h-3 w-[138px] rounded-full" />
                <div className="mt-3 flex items-center gap-1">
                  <span className="data-agent-skeleton size-6 rounded-md" />
                  <span className="data-agent-skeleton h-6 w-12 rounded-md" />
                  <span className="data-agent-skeleton h-2.5 w-16 rounded-full" />
                  <span className="data-agent-skeleton ml-auto size-8 rounded-lg" />
                </div>
              </div>

              <div className="mt-3 grid grid-cols-2 gap-2">
                {QUICK_ACTIONS.map((item) => (
                  <span
                    key={item}
                    className="data-agent-skeleton h-9 rounded-[10px]"
                  />
                ))}
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
}

export default DataAgentWorkspaceSkeleton;
