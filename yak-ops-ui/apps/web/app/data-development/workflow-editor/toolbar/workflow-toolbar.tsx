import {
  PlayCircle,
  Plus,
  Send,
} from "lucide-react";
import { toast } from "@yak-ops/yak-ui";

import { useWorkflowStore } from "../store/workflow-store";

function ToolbarButton({
  label,
  disabled = false,
  icon,
  onClick,
}: {
  label: string;
  disabled?: boolean;
  icon: React.ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="flex h-7 cursor-pointer items-center gap-1.5 rounded-md px-2 text-xs transition-colors hover:bg-[var(--yak-color-hover)] disabled:cursor-not-allowed disabled:opacity-40"
    >
      {icon}
      {label}
    </button>
  );
}

export function WorkflowToolbar() {
  const openSelector = useWorkflowStore(
    (state) => state.openSelector,
  );

  return (
    <div className="flex h-9 shrink-0 items-center gap-1 border-b border-[var(--yak-components-border)] bg-white px-2">
      <ToolbarButton
        label="添加节点"
        icon={<Plus size={14} />}
        onClick={() => openSelector({ kind: "add" })}
      />

      <span className="mx-1 h-4 w-px bg-[var(--yak-components-border)]" />

      <ToolbarButton
        label="运行"
        icon={<PlayCircle size={14} />}
        onClick={() =>
          toast.info("运行", {
            description:
              "运行功能将在后续接入工作流执行接口。",
          })
        }
      />
      <ToolbarButton
        label="发布"
        icon={<Send size={14} />}
        onClick={() =>
          toast.info("发布", {
            description:
              "发布功能将在后续接入工作流版本接口。",
          })
        }
      />
    </div>
  );
}
