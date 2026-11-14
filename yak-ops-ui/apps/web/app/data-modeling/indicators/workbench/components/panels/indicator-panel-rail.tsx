import { History } from "lucide-react";

type IndicatorPanelRailProps = { open: boolean; disabled: boolean; onToggle: () => void };

export function IndicatorPanelRail({ open, disabled, onToggle }: IndicatorPanelRailProps) {
  return (
    <nav aria-label="版本管理" className="flex h-full w-9 shrink-0 flex-col border-l border-[#e5e7eb] bg-white">
      <button type="button" title="版本管理" aria-label="版本管理" aria-pressed={open} disabled={disabled} onClick={onToggle} className={[
        "relative flex w-full shrink-0 cursor-pointer flex-col items-center justify-center border-b border-[#e5e7eb] px-0 py-2 text-xs leading-[18px] transition-colors hover:bg-[#f7f9fc] hover:text-[#315efb] disabled:cursor-not-allowed disabled:opacity-40",
        open ? "bg-white text-[#315efb]" : "bg-white text-[#7c8796]",
      ].join(" ")}>
        {open ? <span aria-hidden="true" className="absolute inset-y-0 left-0 z-[1] w-0.5 bg-[#315efb]" /> : null}
        <History size={14} className="mb-1" />
        <span className="[writing-mode:vertical-rl] [text-orientation:upright] [word-break:keep-all] whitespace-nowrap tracking-[2px]">版本管理</span>
      </button>
    </nav>
  );
}
