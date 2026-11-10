import { useState } from "react";
import {
  BaseEdge,
  EdgeLabelRenderer,
  getBezierPath,
  Position,
  type EdgeProps,
} from "@xyflow/react";
import { Plus } from "lucide-react";

import { useWorkflowStore } from "../store/workflow-store";
import type { WorkflowCanvasEdge } from "../types";

export function WorkflowEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  selected,
  data,
}: EdgeProps<WorkflowCanvasEdge>) {
  const [focused, setFocused] = useState(false);
  const [triggerHovered, setTriggerHovered] = useState(false);
  const openSelector = useWorkflowStore(
    (state) => state.openSelector,
  );

  const [path, labelX, labelY] = getBezierPath({
    sourceX: sourceX - 8,
    sourceY,
    sourcePosition: Position.Right,
    targetX: targetX + 8,
    targetY,
    targetPosition: Position.Left,
    curvature: 0.16,
  });

  const active = Boolean(
    selected || data?.hovered || focused || triggerHovered,
  );

  return (
    <>
      <BaseEdge
        id={id}
        path={path}
        interactionWidth={24}
        style={{
          stroke: active
            ? "var(--yak-color-primary)"
            : "#D0D5DD",
          strokeWidth: 2,
          opacity: 1,
        }}
      />

      <EdgeLabelRenderer>
        <div
          className="nodrag nopan absolute transition-opacity duration-150"
          onMouseEnter={() => setTriggerHovered(true)}
          onMouseLeave={() => setTriggerHovered(false)}
          onFocusCapture={() => setFocused(true)}
          onBlurCapture={() => setFocused(false)}
          style={{
            transform:
              "translate(-50%, -50%) translate(" +
              labelX +
              "px, " +
              labelY +
              "px)",
            pointerEvents: active ? "auto" : "none",
            opacity: active ? 1 : 0,
          }}
        >
          <button
            type="button"
            title="在线路中插入节点"
            aria-label="在线路中插入节点"
            onMouseDown={(event) => event.stopPropagation()}
            onClick={(event) => {
              event.stopPropagation();
              openSelector({
                kind: "edge",
                edgeId: id,
              });
            }}
            className="flex size-6 cursor-pointer items-center justify-center rounded-full border border-[var(--yak-components-border)] bg-white text-[var(--yak-color-primary)] shadow-sm"
          >
            <Plus size={13} />
          </button>
        </div>
      </EdgeLabelRenderer>
    </>
  );
}
