import type { ReactNode } from "react";
import {
  FolderOpen,
  Plus,
  Trash2,
} from "lucide-react";
import {
  Handle,
  Position,
  type NodeProps,
} from "@xyflow/react";
import { toast } from "@yak-ops/yak-ui";

import { useDataDevelopmentWorkbench } from "../../workbench-context";
import { TaskTypeBadgeIcon } from "../components/task-type-badge-icon";
import { useWorkflowStore } from "../store/workflow-store";
import type { WorkflowCanvasNode } from "../types";

function ActionButton({
  label,
  danger = false,
  icon,
  onClick,
}: {
  label: string;
  danger?: boolean;
  icon: ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onMouseDown={(event) => event.stopPropagation()}
      onClick={(event) => {
        event.stopPropagation();
        onClick();
      }}
      className={[
        "nodrag nopan flex size-7 cursor-pointer items-center justify-center transition-colors",
        danger
          ? "text-[var(--yak-color-danger)] hover:bg-red-50"
          : "text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)]",
      ].join(" ")}
    >
      {icon}
    </button>
  );
}

export function WorkflowTaskNode({
  id,
  data,
  selected,
}: NodeProps<WorkflowCanvasNode>) {
  const openSelector = useWorkflowStore(
    (state) => state.openSelector,
  );
  const removeNodes = useWorkflowStore(
    (state) => state.removeNodes,
  );
  const { openDraftTask, openTask } = useDataDevelopmentWorkbench();

  const openCurrentTask = () => {
    if (!data.workflowCode) {
      toast.info("打开节点", {
        description: "当前节点缺少工作流信息。",
      });
      return;
    }

    if (data.task) {
      openTask(data.workflowCode, data.task);
      return;
    }

    openDraftTask(
      data.workflowCode,
      id,
      data.taskType,
      data.name,
    );
  };

  const controlsVisibility = selected
    ? "pointer-events-auto opacity-100"
    : "pointer-events-none opacity-0 group-hover:pointer-events-auto group-hover:opacity-100 focus-within:pointer-events-auto focus-within:opacity-100";

  return (
    <div className="group relative">
      <div
        className={[
          "absolute bottom-full right-0 z-20 mb-2 flex overflow-hidden rounded-md border border-[var(--yak-components-border)] bg-white shadow-md transition-opacity",
          controlsVisibility,
        ].join(" ")}
      >
        <ActionButton
          label="新建上游"
          icon={<Plus size={14} />}
          onClick={() =>
            openSelector({
              kind: "upstream",
              nodeId: id,
            })
          }
        />
        <ActionButton
          label="打开节点"
          icon={<FolderOpen size={14} />}
          onClick={openCurrentTask}
        />
        <ActionButton
          danger
          label="删除"
          icon={<Trash2 size={14} />}
          onClick={() => removeNodes([id])}
        />
      </div>

      <Handle
        type="target"
        position={Position.Left}
        isConnectable
        className="!size-2.5 !cursor-crosshair !border !border-white !bg-[var(--yak-color-primary)]"
      />

      <div
        onDoubleClick={(event) => {
          event.stopPropagation();
          openCurrentTask();
        }}
        className={[
          "min-w-[220px] rounded-lg border bg-white px-4 py-3 shadow-sm transition-[border-color,box-shadow]",
          selected
            ? "border-[var(--yak-color-primary)] shadow-[0_0_0_1px_var(--yak-color-primary)]"
            : "border-[var(--yak-components-border)]",
        ].join(" ")}
      >
        <div className="flex items-center gap-2">
          <TaskTypeBadgeIcon
            taskType={data.taskType || "TASK"}
            size={22}
          />
          <div className="min-w-0">
            <div className="truncate text-[11px] font-medium uppercase tracking-wide text-[var(--yak-components-muted-text)]">
              {data.taskType || "TASK"}
            </div>
            <div className="mt-0.5 truncate text-[13px] font-medium text-[var(--yak-components-panel-text)]">
              {data.name}
            </div>
          </div>
        </div>
      </div>

      <Handle
        type="source"
        position={Position.Right}
        isConnectable
        className="!size-2.5 !cursor-crosshair !border !border-white !bg-[var(--yak-color-primary)]"
      />

      <button
        type="button"
        title="新建下游"
        aria-label="新建下游"
        onMouseDown={(event) => event.stopPropagation()}
        onClick={(event) => {
          event.stopPropagation();
          openSelector({
            kind: "downstream",
            nodeId: id,
          });
        }}
        className={[
          "nodrag nopan absolute left-full top-1/2 z-20 ml-3 flex size-6 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full border border-[var(--yak-components-border)] bg-white text-[var(--yak-components-muted-text)] shadow-sm transition-[opacity,background-color,color]",
          controlsVisibility,
        ].join(" ")}
      >
        <Plus size={13} />
      </button>
    </div>
  );
}
