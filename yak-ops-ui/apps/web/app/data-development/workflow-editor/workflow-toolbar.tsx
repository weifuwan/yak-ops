import { PlayCircle, Plus, Send } from "lucide-react";
import { toast } from "@yak-ops/yak-ui";

export function WorkflowToolbar({
  paletteOpen,
  onTogglePalette,
}: {
  paletteOpen: boolean;
  onTogglePalette: () => void;
}) {
  return (
    <div className="flex h-9 shrink-0 items-center gap-1 border-b border-[var(--yak-components-border)] bg-white px-2">
      <button
        type="button"
        aria-pressed={paletteOpen}
        onClick={onTogglePalette}
        className={[
          "flex h-7 cursor-pointer items-center gap-1.5 rounded-sm px-2 text-xs transition-colors",
          paletteOpen
            ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
            : "hover:bg-[var(--yak-color-hover)]",
        ].join(" ")}
      >
        <Plus size={14} />
        添加节点
      </button>

      <button
        type="button"
        onClick={() =>
          toast.info("运行", {
            description: "运行功能将在后续接入工作流执行接口。",
          })
        }
        className="flex h-7 cursor-pointer items-center gap-1.5 rounded-sm px-2 text-xs hover:bg-[var(--yak-color-hover)]"
      >
        <PlayCircle size={14} />
        运行
      </button>

      <button
        type="button"
        onClick={() =>
          toast.info("发布", {
            description: "发布功能将在后续接入工作流版本接口。",
          })
        }
        className="flex h-7 cursor-pointer items-center gap-1.5 rounded-sm px-2 text-xs hover:bg-[var(--yak-color-hover)]"
      >
        <Send size={14} />
        发布
      </button>
    </div>
  );
}
