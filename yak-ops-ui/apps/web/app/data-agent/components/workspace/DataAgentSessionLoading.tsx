import "./data-agent-loading.css";

function SkeletonBlock({ className = "" }: { className?: string }) {
  return <span aria-hidden="true" className={"data-agent-skeleton " + className} />;
}

export function DataAgentSessionLoading() {
  return (
    <div
      role="status"
      aria-label="正在加载会话"
      aria-busy="true"
      className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-white"
    >
      <span className="sr-only">正在加载会话…</span>

      <header
        aria-hidden="true"
        className="flex h-12 shrink-0 items-center justify-between bg-white px-2.5"
      >
        <div className="flex min-w-0 flex-1 items-center gap-2">
          <SkeletonBlock className="size-7 rounded-md" />
          <SkeletonBlock className="h-3 w-36 rounded-full" />
        </div>
        <div className="flex shrink-0 items-center gap-3 pr-2">
          <SkeletonBlock className="h-2.5 w-12 rounded-full" />
          <SkeletonBlock className="h-2.5 w-16 rounded-full" />
          <SkeletonBlock className="size-7 rounded-full" />
        </div>
      </header>

      <section className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-white">
        <div className="min-h-0 flex-1 overflow-hidden">
          <div className="mx-auto flex w-full max-w-3xl flex-col gap-8 px-6 pb-8 pt-14 max-sm:px-4">
            <div className="flex justify-end">
              <SkeletonBlock className="h-14 w-[56%] max-w-[420px] rounded-2xl" />
            </div>

            <div className="flex items-start gap-3">
              <SkeletonBlock className="size-7 rounded-full" />
              <div className="min-w-0 w-full max-w-[540px] space-y-3">
                <SkeletonBlock className="h-3 w-32 rounded-full" />
                <SkeletonBlock className="h-3 w-full rounded-full" />
                <SkeletonBlock className="h-3 w-[88%] rounded-full" />
                <SkeletonBlock className="h-3 w-[68%] rounded-full" />
              </div>
            </div>

            <div className="flex items-start gap-3">
              <SkeletonBlock className="size-7 rounded-full" />
              <div className="min-w-0 w-full max-w-[500px] space-y-3">
                <SkeletonBlock className="h-3 w-40 rounded-full" />
                <SkeletonBlock className="h-3 w-full rounded-full" />
                <SkeletonBlock className="h-3 w-[74%] rounded-full" />
              </div>
            </div>
          </div>
        </div>

        <div className="shrink-0 px-6 pb-5 pt-3 max-sm:px-4">
          <div className="mx-auto w-full max-w-3xl">
            <div className="rounded-[14px] border border-[#e0e4e3] bg-white p-2.5">
              <SkeletonBlock className="mb-4 h-3 w-[46%] rounded-full" />
              <div className="mt-0.5 flex min-h-7 items-center justify-between gap-3 px-0.5">
                <SkeletonBlock className="size-7 rounded-md" />
                <div className="flex items-center gap-2">
                  <SkeletonBlock className="h-7 w-16 rounded-md" />
                  <SkeletonBlock className="size-8 rounded-full" />
                </div>
              </div>
            </div>
            <SkeletonBlock className="mx-auto mt-2 h-2 w-52 rounded-full" />
          </div>
        </div>
      </section>
    </div>
  );
}

export default DataAgentSessionLoading;
