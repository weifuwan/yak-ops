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
  markerEnd,
  data,
}: EdgeProps<WorkflowCanvasEdge>) {
  const [focused, setFocused] = useState(false);
  const openSelector = useWorkflowStore(
    (state) => state.openSelector,
  );

  const [path, labelX, labelY] = getBezierPath({
    sourceX: sourceX + 4,
    sourceY,
    sourcePosition: Position.Right,
    targetX: targetX - 4,
    targetY,
    targetPosition: Position.Left,
    curvature: 0.18,
  });

  const active = Boolean(
    selected || data?.hovered || focused,
  );

  return (
    <>
      <BaseEdge
        id={id}
        path={path}
        markerEnd={markerEnd}
        interactionWidth={24}
        style={{
          stroke: active
            ? "var(--yak-color-primary)"
            : "var(--yak-components-muted-text)",
          strokeWidth: active ? 2 : 1.25,
        }}
      />

      <EdgeLabelRenderer>
        <button
          type="button"
          title="在线路中插入节点"
          aria-label="在线路中插入节点"
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          onMouseDown={(event) => event.stopPropagation()}
          onClick={(event) => {
            event.stopPropagation();
            openSelector({
              kind: "edge",
              edgeId: id,
            });
          }}
          className={[
            "nodrag nopan absolute flex size-6 cursor-pointer items-center justify-center rounded-full border border-[var(--yak-components-border)] bg-white text-[var(--yak-color-primary)] shadow-sm transition-opacity",
            active
              ? "pointer-events-auto opacity-100"
              : "pointer-events-none opacity-0",
          ].join(" ")}
          style={{
            transform:
              "translate(-50%, -50%) translate(" +
              labelX +
              "px, " +
              labelY +
              "px)",
          }}
        >
          <Plus size={13} />
        </button>
      </EdgeLabelRenderer>
    </>
  );
}
