import { useId, type ReactNode } from "react";
import {
  MoreHorizontal,
  Play,
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

// CONDITIONS is the visual baseline for all right-side branch connectors.
const BRANCH_HANDLE_RIGHT_OFFSET = -21;

function ConnectionHandleIndicator({
  connected,
  side,
  showOnHover = true,
}: {
  connected: boolean;
  side: "left" | "right";
  showOnHover?: boolean;
}) {
  const visibility = connected
    ? "opacity-100"
    : showOnHover
      ? "opacity-0 group-hover:opacity-70"
      : "opacity-0";

  return (
    <span
      aria-hidden="true"
      className={[
        "pointer-events-none absolute top-1 z-0 h-2 w-0.5 rounded-full",
        "bg-[var(--yak-color-primary)] transition-opacity duration-150",
        side === "left" ? "left-1.5" : "right-1.5",
        visibility,
      ].join(" ")}
    />
  );
}

function AddHandleButton({ visibility }: { visibility: string }) {
  const maskId = "workflow-handle-plus-" + useId().replace(/:/g, "");

  return (
    <span
      aria-hidden="true"
      className={[
        "pointer-events-none absolute left-1/2 top-1/2 z-10 flex size-4 -translate-x-1/2 -translate-y-1/2 items-center justify-center",
        "transition-[opacity,transform] duration-150 ease-out",
        "group-hover/handle:scale-125",
        visibility,
      ].join(" ")}
    >
      <span className="absolute inset-0 rounded-full bg-white" />
      <svg viewBox="0 0 18 18" className="relative size-[18px]" focusable="false">
        <defs>
          <mask
            id={maskId}
            maskUnits="userSpaceOnUse"
            maskContentUnits="userSpaceOnUse"
            x="0"
            y="0"
            width="18"
            height="18"
          >
            <rect width="18" height="18" fill="#fff" />
            <path
              d="M9 5v8M5 9h8"
              stroke="#000"
              strokeWidth="2"
              strokeLinecap="round"
            />
          </mask>
        </defs>
        <circle
          cx="9"
          cy="9"
          r="8"
          fill="var(--yak-color-primary)"
          mask={"url(#" + maskId + ")"}
        />
      </svg>
    </span>
  );
}

function ActionButton({
  label,
  danger = false,
  icon,
  onClick,
  disabled = false,
}: {
  label: string;
  danger?: boolean;
  icon: ReactNode;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={disabled}
      onPointerDown={(event) => event.stopPropagation()}
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
  const openEdgeSelector = useWorkflowStore(
    (state) => state.openEdgeSelector,
  );
  const branchOutputs = data.branchOutputsPreview ?? data.branchOutputs ?? [];
  const targetConnected =
    data.connectedTargetHandleIds?.includes("target") ?? false;
  const sourceConnected =
    data.connectedSourceHandleIds?.includes("source") ?? false;
  const { openTask } = useDataDevelopmentWorkbench();

  const openCurrentTask = () => {
    if (!data.workflowCode) {
      toast.info("打开节点", {
        description: "当前节点缺少工作流信息。",
      });
      return;
    }

    if (data.task) {
      openTask(data.workflowCode, data.task, id);
      return;
    }

    toast.warning("节点配置尚未初始化，请重新添加节点");
  };

  const runDisabled = !data.readOnly || data.draft || data.taskCode === null;
  const runDisabledReason = !data.readOnly
    ? "请先发布工作流"
    : "未保存节点暂不支持运行";

  const controlsVisibility = selected || data.menuOpen
    ? "visible pointer-events-auto opacity-100"
    : "invisible pointer-events-none opacity-0 group-hover:visible group-hover:pointer-events-auto group-hover:opacity-100 focus-within:visible focus-within:pointer-events-auto focus-within:opacity-100";

  const addButtonVisibility = data.readOnly
    ? "opacity-0"
    : selected
      ? "opacity-100"
      : "opacity-0 group-hover:opacity-100";

  return (
    <div className="group relative">
      <div
        className={[
          "absolute -top-8 right-0 z-20 flex h-8 origin-bottom-right pb-1 opacity-0 transition-[opacity,visibility] duration-150",
          controlsVisibility,
        ].join(" ")}
      >
        <div
          className="nodrag nopan nowheel flex h-7 items-center gap-0.5 rounded-lg border border-[#D0D5DD] bg-white px-0.5 text-[var(--yak-components-muted-text)] shadow-[0_2px_8px_rgba(16,24,40,0.12)]"
          onPointerDown={(event) => event.stopPropagation()}
          onMouseDown={(event) => event.stopPropagation()}
          onClick={(event) => event.stopPropagation()}
        >
          <ActionButton
            label={runDisabled ? runDisabledReason : "运行节点"}
            icon={<Play size={13} />}
            disabled={runDisabled}
            onClick={() => data.onRun?.()}
          />
          <button
            type="button"
            aria-label="更多操作"
            title="更多操作"
            aria-haspopup="menu"
            aria-expanded={Boolean(data.menuOpen)}
            onPointerDown={(event) => event.stopPropagation()}
            onMouseDown={(event) => event.stopPropagation()}
            onClick={(event) => {
              event.stopPropagation();
              const rect = event.currentTarget.getBoundingClientRect();
              data.onOpenMenu?.({ x: rect.left, y: rect.bottom });
            }}
            className="nodrag nopan flex size-6 cursor-pointer items-center justify-center rounded-md text-[var(--yak-components-muted-text)] transition-colors hover:bg-[#F2F4F7] hover:text-[var(--yak-components-panel-text)]"
          >
            <MoreHorizontal size={15} strokeWidth={1.8} />
          </button>
        </div>
      </div>

      <Handle
        id="target"
        type="target"
        position={Position.Left}
        isConnectable={!data.readOnly}
        aria-label="输入连接点"
        title="输入连接点"
        className={[
          "!z-[10] !size-4 !rounded-none !border-none !bg-transparent !outline-none",
          "!top-4 !-left-[9px] !translate-y-0",
          "before:absolute before:-inset-1",
        ].join(" ")}
      >
        <ConnectionHandleIndicator
          connected={targetConnected}
          side="left"
          showOnHover={!data.readOnly}
        />
      </Handle>

      <div
        onDoubleClick={(event) => {
          event.stopPropagation();
          openCurrentTask();
        }}
        className={[
          "relative flex rounded-2xl border transition-[border-color,box-shadow] duration-200",
          data.focused
            ? "border-[var(--yak-color-primary)] shadow-[0_4px_12px_rgba(49,94,251,0.14)]"
            : selected
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
              size={24}
            />
            <div className="ml-2 min-w-0 grow">
              <div className="truncate text-[13px] font-semibold uppercase text-[var(--yak-components-panel-text)]">
                {data.name}
              </div>
            </div>
          </div>

          <div className="px-3 pb-2">
            {branchOutputs.length > 0 ? (
              <div>
                {branchOutputs.map((output) => {
                  const connected =
                    data.connectedSourceHandleIds?.includes(output.id) ?? false;
                  const labelClass =
                    output.kind === "success"
                      ? "text-[#16A34A]"
                      : output.kind === "failed"
                        ? "text-[#DC6B5D]"
                        : output.kind === "unassigned"
                          ? "text-[#D97706]"
                          : output.kind === "switch-case"
                            ? "text-[#4338CA]"
                            : output.kind === "default"
                              ? "text-[#667085]"
                              : "text-[#475467]";

                  return (
                    <div
                      key={output.id}
                      className="relative flex h-[22px] items-center justify-end pr-7 text-[10px] font-medium"
                    >
                      <span className={"min-w-0 truncate " + labelClass} title={output.label}>{output.label}</span>
                      <Handle
                        id={output.id}
                        type="source"
                        position={Position.Right}
                        isConnectable={!data.readOnly}
                        aria-label={output.label + "分支连接点"}
                        title={output.label + "分支"}
                        style={{ top: "50%", right: BRANCH_HANDLE_RIGHT_OFFSET }}
                        data-workflow-edge-picker-trigger
                        onClick={(event) => {
                          event.stopPropagation();
                          if (data.readOnly) return;
                          const rect = event.currentTarget.getBoundingClientRect();
                          openEdgeSelector({
                            kind: "downstream-popover",
                            nodeId: id,
                            sourceHandle: output.id,
                            anchor: {
                              x: rect.right,
                              y: rect.top + rect.height / 2,
                            },
                          });
                        }}
                        className={[
                          "group/handle !z-[10] !size-4 !rounded-none !border-none !bg-transparent !outline-none",
                          "!translate-y-0",
                          "before:absolute before:-inset-1",
                        ].join(" ")}
                      >
                        <ConnectionHandleIndicator
                          connected={connected}
                          side="right"
                          showOnHover={!data.readOnly}
                        />
                        <AddHandleButton visibility={addButtonVisibility} />
                      </Handle>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="rounded-lg bg-[#f7f8fa] px-2.5 py-1 text-[11px] text-[var(--yak-components-muted-text)]">
                {data.draft ? "未保存节点" : "工作流节点"}
              </div>
            )}
          </div>
        </div>
      </div>

      {branchOutputs.length === 0 ? (
        <Handle
          id="source"
          type="source"
          position={Position.Right}
          isConnectable={!data.readOnly}
          data-workflow-edge-picker-trigger
          onClick={(event) => {
            event.stopPropagation();
            if (data.readOnly) return;
            const rect = event.currentTarget.getBoundingClientRect();
            openEdgeSelector({
              kind: "downstream-popover",
              nodeId: id,
              sourceHandle: "source",
              anchor: {
                x: rect.right,
                y: rect.top + rect.height / 2,
              },
            });
          }}
          aria-label="输出连接点"
          title="输出连接点"
          className={[
            "group/handle !z-[10] !size-4 !rounded-none !border-none !bg-transparent !outline-none",
            "!top-4 !-right-[9px] !translate-y-0",
            "before:absolute before:-inset-1",
          ].join(" ")}
        >
          <ConnectionHandleIndicator
            connected={sourceConnected}
            side="right"
            showOnHover={!data.readOnly}
          />
          <AddHandleButton visibility={addButtonVisibility} />
        </Handle>
      ) : null}
    </div>
  );
}
