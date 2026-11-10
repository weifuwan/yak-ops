import {
  Activity,
  Code2,
  List,
  PlayCircle,
  Plus,
  Send,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { toast } from "@yak-ops/yak-ui";

import { useWorkflowStore } from "../store/workflow-store";
import type { CanvasPanelKey } from "../canvas/canvas-side-panel";

function ToolbarButton({
  label,
  disabled = false,
  icon,
  onClick,
  active = false,
}: {
  label: string;
  disabled?: boolean;
  icon: React.ReactNode;
  onClick: () => void;
  active?: boolean;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      aria-pressed={active}
      className={[
        "flex h-7 cursor-pointer items-center gap-1.5 whitespace-nowrap rounded-md px-2 text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-40",
        active
          ? "bg-[#eef3ff] text-[#315efb]"
          : "text-[#475467] hover:bg-[var(--yak-color-hover)]",
      ].join(" ")}
    >
      {icon}
      {label}
    </button>
  );
}

export function WorkflowToolbar({
  activePanel,
  onTogglePanel,
}: {
  activePanel: CanvasPanelKey | null;
  onTogglePanel: (panel: CanvasPanelKey) => void;
}) {
  const openSelector = useWorkflowStore(
    (state) => state.openSelector,
  );
  const navigate = useNavigate();

  return (
    <div className="flex h-9 shrink-0 items-center gap-1 border-b border-[#e5e7eb] bg-white px-2">
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

      <div className="ml-auto flex min-w-0 items-center gap-1 border-l border-[#e5e7eb] pl-2">
        <ToolbarButton
          label={
            activePanel === "nodes"
              ? "隐藏内部节点列表"
              : "显示内部节点列表"
          }
          icon={<List size={14} />}
          active={activePanel === "nodes"}
          onClick={() => onTogglePanel("nodes")}
        />
        <ToolbarButton
          label={activePanel === "spec" ? "隐藏 Spec" : "显示 Spec"}
          icon={<Code2 size={14} />}
          active={activePanel === "spec"}
          onClick={() => onTogglePanel("spec")}
        />
        <ToolbarButton
          label="运维中心"
          icon={<Activity size={14} />}
          onClick={() => navigate("/operations/offline-tasks")}
        />
      </div>
    </div>
  );
}
