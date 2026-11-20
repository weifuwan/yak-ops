import { PanelLeft } from "lucide-react";

type DataAgentConversationHeaderProps = {
  title: string;
  isSending: boolean;
  sidebarOpen: boolean;
  onToggleSidebar: () => void;
};

export function DataAgentConversationHeader({
  title,
  isSending,
  sidebarOpen,
  onToggleSidebar,
}: DataAgentConversationHeaderProps) {
  return (
    <header className="flex h-12 shrink-0 items-center justify-between border-b border-[#e8eae9] bg-[#fafafa] px-4">
      <div className="flex min-w-0 items-center gap-3">
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

        <div className="flex min-w-0 flex-col justify-center gap-0.5">
          <h1 className="m-0 truncate text-[13px] font-semibold leading-4 text-[#303936]" title={title}>
            {title}
          </h1>
          <span className="text-[10px] leading-3 text-[#929b97]">Data Agent</span>
        </div>
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
    </header>
  );
}

export default DataAgentConversationHeader;
