import {
  Check,
  ChevronDown,
  Sparkles,
} from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";

type ModelSelectorProps = {
  value: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelect: (value: string) => void;
};

export function ModelSelector({
  value,
  open,
  onOpenChange,
  onSelect,
}: ModelSelectorProps) {
  return (
    <DropdownMenu open={open} onOpenChange={onOpenChange}>
      <DropdownMenuTrigger
        aria-label="选择模型"
        className={
          "flex h-8 cursor-pointer items-center gap-1.5 rounded-full px-2.5 text-xs font-medium transition-colors hover:bg-black/[0.045] hover:text-[#20201e] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/30 " +
          (open ? "bg-[#f4f4f2] text-[#20201e]" : "text-[#626262]")
        }
      >
        <Sparkles size={13} strokeWidth={1.8} />
        <span>{value}</span>
        <ChevronDown
          size={13}
          className={"transition-transform duration-150 " + (open ? "rotate-180" : "")}
        />
      </DropdownMenuTrigger>

      <DropdownMenuContent
        side="bottom"
        align="end"
        sideOffset={8}
        className="w-56 rounded-[14px] border border-[#e8e8e5] bg-white p-1.5 text-[#20201e] shadow-[0_10px_30px_rgba(0,0,0,0.12)]"
      >
        <DropdownMenuItem
          role="menuitemradio"
          aria-checked={value === "DeepSeek"}
          className="min-h-[56px] cursor-pointer items-start justify-between gap-3 rounded-[10px] px-3 py-2 data-[highlighted]:bg-[#f5f5f3]"
          onClick={() => onSelect("DeepSeek")}
        >
          <span className="min-w-0 flex-1">
            <span className="block text-[13px] font-medium leading-5">DeepSeek</span>
            <span className="mt-0.5 block text-[11px] leading-4 text-[#999999]">
              模型由 Agent 服务端配置决定
            </span>
          </span>
          {value === "DeepSeek" ? (
            <Check size={15} className="mt-0.5 shrink-0 text-[#fe2c55]" />
          ) : null}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
