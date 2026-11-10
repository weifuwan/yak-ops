import { useState } from "react";
import {
  LayoutTemplate,
  PlayCircle,
  Plus,
  Redo2,
  Send,
  Undo2,
} from "lucide-react";
import { useReactFlow } from "@xyflow/react";
import { toast } from "@yak-ops/yak-ui";

import { layoutWorkflowGraph } from "../layout/elk-layout";
import {
  useWorkflowHistory,
  useWorkflowStore,
  useWorkflowStoreApi,
} from "../store/workflow-store";
import type {
  WorkflowCanvasEdge,
  WorkflowCanvasNode,
} from "../types";

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
  const store = useWorkflowStoreApi();
  const reactFlow =
    useReactFlow<WorkflowCanvasNode, WorkflowCanvasEdge>();
  const [layoutPending, setLayoutPending] =
    useState(false);

  const openSelector = useWorkflowStore(
    (state) => state.openSelector,
  );
  const replaceGraph = useWorkflowStore(
    (state) => state.replaceGraph,
  );

  const undo = useWorkflowHistory(
    (state) => state.undo,
  );
  const redo = useWorkflowHistory(
    (state) => state.redo,
  );
  const canUndo = useWorkflowHistory(
    (state) => state.pastStates.length > 0,
  );
  const canRedo = useWorkflowHistory(
    (state) => state.futureStates.length > 0,
  );

  const handleAutoLayout = async () => {
    if (layoutPending) return;

    setLayoutPending(true);
    try {
      const state = store.getState();
      const layouted = await layoutWorkflowGraph({
        nodes: state.nodes,
        edges: state.edges,
      });
      replaceGraph(layouted);

      window.requestAnimationFrame(() => {
        void reactFlow.fitView({
          padding: 0.22,
          duration: 220,
          maxZoom: 1,
        });
      });
    } finally {
      setLayoutPending(false);
    }
  };

  return (
    <div className="flex h-9 shrink-0 items-center gap-1 border-b border-[var(--yak-components-border)] bg-white px-2">
      <ToolbarButton
        label="添加节点"
        icon={<Plus size={14} />}
        onClick={() => openSelector({ kind: "add" })}
      />

      <span className="mx-1 h-4 w-px bg-[var(--yak-components-border)]" />

      <ToolbarButton
        label="撤销"
        disabled={!canUndo}
        icon={<Undo2 size={14} />}
        onClick={undo}
      />
      <ToolbarButton
        label="重做"
        disabled={!canRedo}
        icon={<Redo2 size={14} />}
        onClick={redo}
      />
      <ToolbarButton
        label={layoutPending ? "布局中" : "自动布局"}
        disabled={layoutPending}
        icon={<LayoutTemplate size={14} />}
        onClick={() => void handleAutoLayout()}
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
