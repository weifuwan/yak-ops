import { PanelLeft, PanelRight, X } from "lucide-react";

type DataAgentConversationHeaderProps = {
  title: string;
  isSending: boolean;
  sidebarOpen: boolean;
  detailsOpen: boolean;
  detailsWidth: number;
  onToggleSidebar: () => void;
  onToggleDetails: () => void;
};

export function DataAgentConversationHeader({
  title,
  isSending,
  sidebarOpen,
  detailsOpen,
  detailsWidth,
  onToggleSidebar,
  onToggleDetails,
}: DataAgentConversationHeaderProps) {
  return (
    <header className="relative z-10 flex h-12 shrink-0 items-center border-b border-[#e8eae9] bg-white">
      <div className="flex h-full min-w-0 flex-1 items-center gap-3 px-3">
        <button
          type="button"
          aria-label={sidebarOpen ? "收起侧边栏" : "展开侧边栏"}
          aria-expanded={sidebarOpen}
          title={sidebarOpen ? "收起侧边栏" : "展开侧边栏"}
          className="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-lg border border-[#e1e5e4] bg-white text-[#66716f] transition-colors hover:border-[#cbd2d1] hover:bg-[#f3f5f4] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25"
          onClick={onToggleSidebar}
        >
          <PanelLeft size={15} strokeWidth={1.5} />
        </button>

        <span aria-hidden="true" className="h-5 w-px shrink-0 bg-[#e5e8e7]" />

        <div className="flex min-w-0 flex-1 flex-col justify-center gap-0.5">
          <h1 className="m-0 truncate text-[13px] font-semibold leading-4 text-[#303936]" title={title}>
            {title}
          </h1>
          <span className="text-[10px] leading-3 text-[#929b97]">Data Agent</span>
        </div>

        <div
          className="flex shrink-0 items-center gap-2 text-[11px] text-[#87918c]"
          role="status"
          aria-live="polite"
        >
          <span
            aria-hidden="true"
            className={
              "size-1.5 rounded-full " +
              (isSending ? "animate-pulse bg-[#d6a34b]" : "bg-[#91a39a]")
            }
          />
          {isSending ? "正在处理" : "已就绪"}
        </div>

        <button
          type="button"
          aria-label={detailsOpen ? "关闭执行详情" : "打开执行详情"}
          aria-expanded={detailsOpen}
          title={detailsOpen ? "关闭执行详情" : "打开执行详情"}
          className="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-lg border border-[#e1e5e4] bg-white text-[#66716f] transition-colors hover:border-[#cbd2d1] hover:bg-[#f3f5f4] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25 md:hidden"
          onClick={onToggleDetails}
        >
          {detailsOpen ? <X size={15} /> : <PanelRight size={15} />}
        </button>

        {!detailsOpen ? (
          <button
            type="button"
            aria-label="打开执行详情"
            title="打开执行详情"
            className="hidden h-7 shrink-0 cursor-pointer items-center gap-1.5 rounded-md border border-[#e1e5e4] px-2 text-[11px] text-[#66716f] transition-colors hover:bg-[#f3f5f4] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25 md:flex"
            onClick={onToggleDetails}
          >
            <PanelRight size={14} />
            Details
          </button>
        ) : null}
      </div>

      {detailsOpen ? (
        <div
          style={{ width: String(detailsWidth) + "px" }}
          className="relative hidden h-full shrink-0 items-center border-l border-[#ecefed] px-3 md:flex"
        >
          <span className="pointer-events-none absolute left-1/2 -translate-x-1/2 text-[13px] font-semibold text-[#303936]">
            Details
          </span>

          <div className="ml-auto flex shrink-0 items-center gap-1">
            <button
              type="button"
              aria-current="page"
              className="flex h-7 cursor-default items-center rounded-md border border-[#dfe4e2] bg-[#f3f5f4] px-2 text-[11px] font-medium text-[#3f4945]"
            >
              Timeline
            </button>
            <button
              type="button"
              disabled
              title="变更记录暂未接入"
              className="flex h-7 cursor-not-allowed items-center rounded-md px-2 text-[11px] text-[#a6aeaa]"
            >
              Changes
            </button>
            <span aria-hidden="true" className="mx-1 h-4 w-px bg-[#e7eae8]" />
            <button
              type="button"
              aria-label="关闭执行详情"
              title="关闭执行详情"
              className="flex size-7 cursor-pointer items-center justify-center rounded-md text-[#87928f] transition-colors hover:bg-[#f2f4f3] hover:text-[#303936] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25"
              onClick={onToggleDetails}
            >
              <X size={15} />
            </button>
          </div>
        </div>
      ) : null}
    </header>
  );
}

export default DataAgentConversationHeader;
