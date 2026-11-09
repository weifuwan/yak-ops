import {
  Handle,
  Position,
  type Node,
  type NodeProps,
} from "@xyflow/react";

export type WorkflowNodeData = {
  name: string;
  taskType: string;
};

export type WorkflowCanvasNode = Node<WorkflowNodeData, "workflowTask">;

export function WorkflowTaskNode({
  data,
  selected,
}: NodeProps<WorkflowCanvasNode>) {
  return (
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
  );
}
