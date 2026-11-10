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
} from "reactflow";
import { toast } from "@yak-ops/yak-ui";

import { useDataDevelopmentWorkbench } from "../../workbench-context";
import { TaskTypeBadgeIcon } from "../components/task-type-badge-icon";
import { useWorkflowStore } from "../store/workflow-store";
import type { WorkflowNodeData } from "../types";

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
        "nodrag nopan flex size-6 cursor-pointer items-center justify-center rounded-md transition-colors",
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
}: NodeProps<WorkflowNodeData>) {
  const openSelector = useWorkflowStore(
    (state) => state.openSelector,
  );
  const removeNodes = useWorkflowStore(
    (state) => state.removeNodes,
  );
  const hasIncomingEdge = useWorkflowStore(
    (state) => state.edges.some((edge) => edge.target === id),
  );
  const hasOutgoingEdge = useWorkflowStore(
    (state) => state.edges.some((edge) => edge.source === id),
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

  const addButtonVisibility = selected
    ? "opacity-100"
    : "opacity-0 group-hover:opacity-100";

  return (
    <div className="group relative">
      <div
        className={[
          "pointer-events-none absolute -top-8 right-0 z-20 flex h-8 origin-bottom-right pb-1 opacity-0 transition-opacity",
          controlsVisibility,
        ].join(" ")}
      >
        <div
          className="nodrag nopan nowheel flex h-7 items-center rounded-lg border border-[var(--yak-components-border)] bg-white/95 px-0.5 text-[var(--yak-components-muted-text)] shadow-md backdrop-blur-[5px]"
          onMouseDown={(event) => event.stopPropagation()}
          onClick={(event) => event.stopPropagation()}
        >
          <ActionButton
            label="新建上游"
            icon={<Plus size={13} />}
            onClick={() =>
              openSelector({
                kind: "upstream",
                nodeId: id,
              })
            }
          />
          <ActionButton
            label="打开节点"
            icon={<FolderOpen size={13} />}
            onClick={openCurrentTask}
          />
          <ActionButton
            danger
            label="删除"
            icon={<Trash2 size={13} />}
            onClick={() => removeNodes([id])}
          />
        </div>
      </div>

      <Handle
        id="target"
        type="target"
        position={Position.Left}
        isConnectable
        className={[
          "!z-[1] !size-4 !rounded-none !border-none !bg-transparent !outline-none",
          "!top-4 !-left-[9px] !translate-y-0",
          "before:absolute before:-inset-1",
          "after:absolute after:left-1.5 after:top-1 after:h-2 after:w-0.5 after:bg-[#98A2B3]",
          "after:opacity-100",
        ].join(" ")}
      />

      <div
        onDoubleClick={(event) => {
          event.stopPropagation();
          openCurrentTask();
        }}
        className={[
          "relative flex rounded-2xl border transition-colors",
          selected
            ? "border-[var(--yak-color-primary)]"
            : "border-transparent",
        ].join(" ")}
      >
        <div
          className={[
            "relative w-60 rounded-[15px] border border-transparent bg-white pb-1",
            "shadow-[0_1px_2px_rgba(16,24,40,0.05)]",
            "transition-[box-shadow,border-color] duration-150",
            "hover:shadow-[0_8px_24px_rgba(16,24,40,0.12)]",
          ].join(" ")}
        >
          <div className="flex items-center rounded-t-2xl px-3 pb-2 pt-3">
            <TaskTypeBadgeIcon
              taskType={data.taskType || "TASK"}
              size={28}
            />
            <div className="ml-2 min-w-0 grow">
              <div className="truncate text-[13px] font-semibold uppercase text-[var(--yak-components-panel-text)]">
                {data.name}
              </div>
              <div className="mt-0.5 truncate text-[10px] font-medium uppercase tracking-wide text-[var(--yak-components-muted-text)]">
                {data.taskType || "TASK"}
              </div>
            </div>
          </div>

          <div className="px-3 pb-2">
            <div className="rounded-lg bg-[#f7f8fa] px-2.5 py-2 text-[11px] text-[var(--yak-components-muted-text)]">
              {data.draft ? "未保存节点" : "工作流节点"}
            </div>
          </div>
        </div>
      </div>

      <Handle
        id="source"
        type="source"
        position={Position.Right}
        isConnectable
        onClick={(event) => {
          event.stopPropagation();
          openSelector({
            kind: "downstream",
            nodeId: id,
          });
        }}
        className={[
          "group/handle !z-[1] !size-4 !rounded-none !border-none !bg-transparent !outline-none",
          "!top-4 !-right-[9px] !translate-y-0",
          "before:absolute before:-inset-1",
          "after:absolute after:right-1.5 after:top-1 after:h-2 after:w-0.5 after:bg-[#98A2B3]",
          hasOutgoingEdge ? "after:opacity-100" : "after:opacity-0",
        ].join(" ")}
      >
        <span
          aria-hidden="true"
          className={[
            "pointer-events-none absolute left-1/2 top-1/2 flex size-4 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full",
            "bg-[var(--yak-color-primary)] text-white shadow-sm",
            "transition-[opacity,transform] duration-150 ease-out",
            "group-hover/handle:scale-125",
            addButtonVisibility,
          ].join(" ")}
        >
          <Plus size={10} strokeWidth={2.5} />
        </span>
      </Handle>
    </div>
  );
}
