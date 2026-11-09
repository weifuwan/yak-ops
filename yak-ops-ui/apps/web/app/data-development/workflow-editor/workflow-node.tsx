import type { ReactNode } from "react";
import {
  Handle,
  Position,
  type Node,
  type NodeProps,
} from "@xyflow/react";
import {
  FolderOpen,
  Plus,
  Trash2,
} from "lucide-react";

export type WorkflowNodeAction =
  | "create-upstream"
  | "open"
  | "delete"
  | "create-downstream";

export type WorkflowNodeData = {
  taskCode: number;
  name: string;
  taskType: string;
  onAction?: (action: WorkflowNodeAction, taskCode: number) => void;
};

export type WorkflowCanvasNode = Node<WorkflowNodeData, "workflowTask">;

function NodeActionButton({
  danger = false,
  icon,
  label,
  onClick,
}: {
  danger?: boolean;
  icon: ReactNode;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={[
        "nodrag nopan inline-flex h-7 cursor-pointer items-center gap-1 whitespace-nowrap border-0 bg-white px-2 text-[12px] shadow-none outline-none transition-colors",
        danger
          ? "text-[var(--yak-color-danger)] hover:bg-red-50"
          : "text-[var(--yak-components-panel-text)] hover:bg-[var(--yak-color-hover)]",
      ].join(" ")}
      onMouseDown={(event) => event.stopPropagation()}
      onClick={(event) => {
        event.stopPropagation();
        onClick();
      }}
    >
      {icon}
      <span>{label}</span>
    </button>
  );
}

export function WorkflowTaskNode({
  data,
  selected,
}: NodeProps<WorkflowCanvasNode>) {
  const visibilityClass = selected
    ? "pointer-events-auto opacity-100"
    : "pointer-events-none opacity-0 group-hover:pointer-events-auto group-hover:opacity-100";

  const trigger = (action: WorkflowNodeAction) => {
    data.onAction?.(action, data.taskCode);
  };

  return (
    <div className="group relative">
      <div
        className={[
          "absolute bottom-full left-1/2 z-20 mb-2 flex -translate-x-1/2 overflow-hidden rounded-sm border border-[var(--yak-components-border)] bg-white shadow-sm transition-opacity duration-100",
          visibilityClass,
        ].join(" ")}
      >
        <NodeActionButton
          label="新建上游"
          icon={<Plus size={13} aria-hidden="true" />}
          onClick={() => trigger("create-upstream")}
        />
        <span className="w-px shrink-0 bg-[var(--yak-components-border)]" />
        <NodeActionButton
          label="打开节点"
          icon={<FolderOpen size={13} aria-hidden="true" />}
          onClick={() => trigger("open")}
        />
        <span className="w-px shrink-0 bg-[var(--yak-components-border)]" />
        <NodeActionButton
          danger
          label="删除"
          icon={<Trash2 size={13} aria-hidden="true" />}
          onClick={() => trigger("delete")}
        />
      </div>

      <div
        className={[
          "min-w-[220px] rounded-md border bg-white px-4 py-3 shadow-sm transition-[border-color,box-shadow]",
          selected
            ? "border-[var(--yak-color-primary)] shadow-[0_0_0_1px_var(--yak-color-primary)]"
            : "border-[var(--yak-components-border)]",
        ].join(" ")}
      >
        <Handle
          type="target"
          position={Position.Top}
          isConnectable={false}
          className="!size-2 !border !border-white !bg-[var(--yak-color-primary)]"
        />
        <div className="truncate text-[11px] font-medium uppercase tracking-wide text-[var(--yak-components-muted-text)]">
          {data.taskType || "TASK"}
        </div>
        <div className="mt-1 truncate text-[13px] font-medium text-[var(--yak-components-panel-text)]">
          {data.name}
        </div>
        <Handle
          type="source"
          position={Position.Bottom}
          isConnectable={false}
          className="!size-2 !border !border-white !bg-[var(--yak-color-primary)]"
        />
      </div>

      <div
        className={[
          "absolute left-1/2 top-full z-20 mt-2 -translate-x-1/2 overflow-hidden rounded-sm border border-[var(--yak-components-border)] bg-white shadow-sm transition-opacity duration-100",
          visibilityClass,
        ].join(" ")}
      >
        <NodeActionButton
          label="新建下游"
          icon={<Plus size={13} aria-hidden="true" />}
          onClick={() => trigger("create-downstream")}
        />
      </div>
    </div>
  );
}
