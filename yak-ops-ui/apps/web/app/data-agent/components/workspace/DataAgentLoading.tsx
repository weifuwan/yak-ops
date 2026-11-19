import "./data-agent-loading.css";

type DataAgentLoadingProps = {
  sidebarWidth?: number;
  exiting?: boolean;
};

function SkeletonBlock({
  className = "",
}: {
  className?: string;
}) {
  return <span aria-hidden="true" className={"data-agent-skeleton " + className} />;
}

export function DataAgentLoading({
  sidebarWidth = 256,
  exiting = false,
}: DataAgentLoadingProps) {
  return (
    <div
      role="status"
      aria-label="正在加载 Data Agent"
      aria-busy="true"
      aria-hidden={exiting}
      className={
        "fixed inset-0 z-[100] flex h-dvh w-screen flex-col overflow-hidden bg-[#f0f0f0] p-2 text-[#20201e] transition-opacity duration-200 ease-out motion-reduce:transition-none max-md:p-0 " +
        (exiting ? "pointer-events-none opacity-0" : "opacity-100")
      }
    >
      <span className="sr-only">正在加载 Data Agent…</span>

      <header
        aria-hidden="true"
        className="flex h-7 shrink-0 items-center justify-between px-0.5 pb-2"
      >
        <div className="flex items-center gap-2">
          <SkeletonBlock className="size-4 rounded-[5px]" />
          <SkeletonBlock className="h-2.5 w-12 rounded-full" />
          <SkeletonBlock className="size-2 rounded-full" />
        </div>
        <div className="flex items-center gap-2">
          <SkeletonBlock className="h-6 w-10 rounded-md" />
          <SkeletonBlock className="h-2.5 w-20 rounded-full" />
          <SkeletonBlock className="size-6 rounded-md" />
          <SkeletonBlock className="h-6 w-16 rounded-md" />
          <SkeletonBlock className="h-6 w-16 rounded-md" />
        </div>
      </header>

      <div className="flex min-h-0 min-w-0 flex-1 overflow-hidden">
        <aside
          aria-hidden="true"
          className="flex h-full shrink-0 flex-col gap-3 overflow-hidden bg-[#f0f0f0] px-2 pb-2 pt-3 max-md:hidden"
          style={{ width: sidebarWidth }}
        >
          <div className="flex h-6 items-center gap-2 px-0.5">
            <SkeletonBlock className="h-4 w-4 rounded-[5px]" />
            <SkeletonBlock className="h-2.5 w-16 rounded-full" />
          </div>
          <SkeletonBlock className="mt-1 h-8 w-full rounded-[9px]" />
          <div className="flex items-center gap-2 px-2 pt-1">
            <SkeletonBlock className="h-2 w-5 rounded-full" />
            <SkeletonBlock className="ml-auto size-3 rounded-full" />
            <SkeletonBlock className="size-3 rounded-full" />
          </div>
          <div className="mt-1 border-t border-[#e2e5e4] pt-3">
            <SkeletonBlock className="mb-3 ml-2 h-2 w-12 rounded-full" />
            <SkeletonBlock className="mb-2 ml-2 h-2 w-[82%] rounded-full" />
            <SkeletonBlock className="ml-2 h-2 w-[68%] rounded-full" />
          </div>
          <div className="mt-auto flex items-center gap-2 border-t border-[#e2e5e4] px-2 pt-3">
            <SkeletonBlock className="size-7 rounded-full" />
            <SkeletonBlock className="h-2.5 w-20 rounded-full" />
          </div>
        </aside>

        <main className="flex min-h-0 min-w-0 flex-1 bg-[#f0f0f0] p-2 max-md:p-0">
          <section className="relative flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-[#e2e6e5] bg-white shadow-[0_1px_2px_rgba(20,34,35,0.03)] max-md:rounded-none max-md:border-0">
            <div className="absolute inset-x-0 top-0 flex items-center justify-between px-2 pt-2">
              <SkeletonBlock className="size-7 rounded-lg" />
              <div className="flex items-center gap-2">
                <SkeletonBlock className="size-6 rounded-md" />
                <SkeletonBlock className="h-6 w-16 rounded-md" />
                <SkeletonBlock className="h-6 w-16 rounded-md" />
              </div>
            </div>

            <div className="flex min-h-0 flex-1 items-center justify-center px-5 py-12">
              <div className="w-full max-w-[494px]">
                <div className="rounded-xl border border-[#e3e6e5] bg-white p-3">
                  <SkeletonBlock className="mb-4 h-2.5 w-[34%] rounded-full" />
                  <div className="flex items-center gap-1.5">
                    <SkeletonBlock className="size-6 rounded-md" />
                    <SkeletonBlock className="h-6 w-11 rounded-md" />
                    <SkeletonBlock className="h-2 w-16 rounded-full" />
                    <SkeletonBlock className="ml-auto size-8 rounded-md" />
                  </div>
                </div>
                <div className="mt-3 grid grid-cols-2 gap-2">
                  {Array.from({ length: 4 }, (_, index) => (
                    <SkeletonBlock key={index} className="h-9 rounded-md" />
                  ))}
                </div>
              </div>
            </div>
          </section>
        </main>
      </div>
    </div>
  );
}

export default DataAgentLoading;
