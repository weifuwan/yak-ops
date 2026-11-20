import {
  ArrowLeft,
  ChevronsUpDown,
  Clock3,
  Cloud,
  List,
  PanelLeft,
  PanelRight,
  PanelRightClose,
  SquarePlus,
} from "lucide-react";

type DataAgentConversationHeaderProps = {
  title: string;
  isSending: boolean;
  sidebarOpen: boolean;
  detailsOpen: boolean;
  detailsWidth: number;
  expandAll: boolean;
  onToggleSidebar: () => void;
  onToggleDetails: () => void;
  onToggleExpandAll: () => void;
  onBackToLatest: () => void;
};

const PREVIEW_DURATION = "6m 40s";
const PREVIEW_SCORE = "4.50";

export function DataAgentConversationHeader({
  title,
  isSending,
  sidebarOpen,
  detailsOpen,
  detailsWidth,
  expandAll,
  onToggleSidebar,
  onToggleDetails,
  onToggleExpandAll,
  onBackToLatest,
}: DataAgentConversationHeaderProps) {
  const statusLabel = isSending ? "正在处理" : "已就绪";

  return (
    <header className="relative z-10 flex h-12 shrink-0 items-center bg-white">
      <div className="flex h-full min-w-0 flex-1 items-center gap-2 px-2.5">
        <button
          type="button"
          aria-label={sidebarOpen ? "收起侧边栏" : "展开侧边栏"}
          aria-expanded={sidebarOpen}
          title={sidebarOpen ? "收起侧边栏" : "展开侧边栏"}
          className="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-md bg-transparent text-[#7b8581] transition-colors hover:bg-[#f2f4f3] hover:text-[#303936] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25"
          onClick={onToggleSidebar}
        >
          <PanelLeft size={16} strokeWidth={1.5} />
        </button>

        <h1
          className="m-0 min-w-0 flex-1 truncate text-sm font-[450] leading-tight text-[#303936]"
          title={title}
        >
          {title}
        </h1>
      </div>

      {detailsOpen ? (
        <>
          <button
            type="button"
            aria-label="收起执行详情"
            aria-expanded={detailsOpen}
            title="收起执行详情"
            style={{ right: String(detailsWidth + 12) + "px" }}
            className="absolute top-1/2 z-20 hidden size-7 -translate-y-1/2 cursor-pointer items-center justify-center rounded-md bg-transparent text-[#7b8581] transition-colors hover:bg-[#f2f4f3] hover:text-[#303936] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25 lg:flex"
            onClick={onToggleDetails}
          >
            <PanelRightClose size={16} strokeWidth={1.5} />
          </button>

          <div
            style={{ width: String(detailsWidth) + "px" }}
            className="relative mr-2 hidden h-full shrink-0 items-center justify-between gap-2 border-y border-[#e7e9ea] bg-[#f7f8f8] px-2 @container lg:flex"
          >
            <div className="relative z-10 flex min-w-0 shrink-0 items-center gap-1.5">
              <button
                type="button"
                aria-label="Back to latest"
                title="Back to latest"
                className="flex h-7 shrink-0 cursor-pointer items-center gap-1.5 rounded-full border border-[#d7dcde] bg-white px-2.5 text-xs font-medium text-[#30383c] shadow-[0_1px_2px_rgba(0,0,0,0.06)] transition-colors hover:bg-[#f9fafb] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/20"
                onClick={onBackToLatest}
              >
                <ArrowLeft size={14} strokeWidth={1.7} />
                <span>Back to latest</span>
              </button>

              <span className="flex shrink-0 cursor-default items-center gap-1 px-1 text-xs tabular-nums text-[#737d82] @max-[28rem]:hidden">
                <Clock3 size={12} strokeWidth={1.6} />
                {PREVIEW_DURATION}
              </span>

              <span className="flex shrink-0 cursor-default items-center gap-1 px-1 text-xs tabular-nums text-[#737d82] @max-[28rem]:hidden">
                <span className="flex size-3.5 items-center justify-center rounded-full border border-current">
                  <Cloud size={8} strokeWidth={1.6} />
                </span>
                {PREVIEW_SCORE}
              </span>
            </div>

            <span className="pointer-events-none absolute inset-0 z-0 flex items-center justify-center text-sm font-semibold text-[#202629]">
              Details
            </span>

            <div className="relative z-10 flex shrink-0 items-center gap-1">
              <button
                type="button"
                aria-label={expandAll ? "Collapse all" : "Expand all"}
                aria-pressed={expandAll}
                title={expandAll ? "Collapse all" : "Expand all"}
                className="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-full border border-[#d7dcde] bg-white text-[#485257] shadow-[0_1px_2px_rgba(0,0,0,0.06)] transition-colors hover:bg-[#f8f9fa] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/20"
                onClick={onToggleExpandAll}
              >
                <ChevronsUpDown size={14} strokeWidth={1.6} />
              </button>

              <div
                role="tablist"
                aria-label="Details view"
                className="flex h-8 shrink-0 items-center gap-0.5 rounded-full border border-[#d7dcde] bg-[#f0f2f3] p-[2px]"
              >
                <button
                  type="button"
                  role="tab"
                  aria-selected="true"
                  className="flex h-7 cursor-default items-center gap-1.5 rounded-full border border-[#e0e3e5] bg-white px-2.5 text-xs font-medium text-[#30383c] shadow-[0_1px_2px_rgba(0,0,0,0.07)]"
                >
                  <span className="flex size-3.5 items-center justify-center rounded-[3px] border border-current">
                    <List size={9} strokeWidth={1.8} />
                  </span>
                  Timeline
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected="false"
                  disabled
                  title="变更记录暂未接入"
                  className="flex h-7 cursor-not-allowed items-center gap-1.5 rounded-full px-2 text-xs text-[#899297] transition-colors"
                >
                  <SquarePlus size={12} strokeWidth={1.6} />
                  Changes
                </button>
              </div>
            </div>
          </div>
        </>
      ) : (
        <div className="flex h-full shrink-0 items-center gap-2 px-2.5">
          <div
            role="status"
            aria-live="polite"
            aria-label={statusLabel}
            title={statusLabel}
            className="flex shrink-0 items-center"
          >
            <span
              aria-hidden="true"
              className={
                "size-1.5 rounded-full " +
                (isSending ? "animate-pulse bg-[#d6a34b]" : "bg-[#91a39a]")
              }
            />
          </div>
          <button
            type="button"
            aria-label="打开执行详情"
            aria-expanded={detailsOpen}
            title="打开执行详情"
            className="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-md bg-transparent text-[#7b8581] transition-colors hover:bg-[#f2f4f3] hover:text-[#303936] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25"
            onClick={onToggleDetails}
          >
            <PanelRight size={16} strokeWidth={1.5} />
          </button>
        </div>
      )}
    </header>
  );
}

export default DataAgentConversationHeader;
