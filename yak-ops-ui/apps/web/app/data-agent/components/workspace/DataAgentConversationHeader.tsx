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
        <div
          style={{ width: String(detailsWidth) + "px" }}
          className="mr-2 hidden h-full shrink-0 items-center justify-end gap-1 px-2.5 lg:flex"
        >
          <div
            role="status"
            aria-live="polite"
            aria-label={statusLabel}
            title={statusLabel}
            className="mr-1 flex shrink-0 items-center"
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
            aria-current="page"
            className="flex h-7 cursor-default items-center rounded-md bg-[#f1f3f2] px-2 text-[11px] font-medium text-[#3f4945]"
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
          <button
            type="button"
            aria-label="关闭执行详情"
            title="关闭执行详情"
            className="flex size-7 cursor-pointer items-center justify-center rounded-md bg-transparent text-[#7b8581] transition-colors hover:bg-[#f2f4f3] hover:text-[#303936] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25"
            onClick={onToggleDetails}
          >
            <X size={16} />
          </button>
        </div>
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
