import {
  getBezierPath,
  getSmoothStepPath,
  Position,
  type ConnectionLineComponentProps,
} from "reactflow";

import { useWorkflowStore } from "../store/workflow-store";

export function WorkflowConnectionLine({
  fromX,
  fromY,
  toX,
  toY,
}: ConnectionLineComponentProps) {
  const edgeStyle = useWorkflowStore(
    (state) => state.edgeStyle,
  );
  const pathOptions = {
    sourceX: fromX,
    sourceY: fromY,
    sourcePosition: Position.Right,
    targetX: toX,
    targetY: toY,
    targetPosition: Position.Left,
  };
  const [path] =
    edgeStyle === "bezier"
      ? getBezierPath({ ...pathOptions, curvature: 0.16 })
      : getSmoothStepPath({
          ...pathOptions,
          borderRadius: edgeStyle === "step" ? 0 : 8,
        });

  return (
    <g>
      <path
        d={path}
        fill="none"
        stroke="#D0D5DD"
        strokeWidth={2}
      />
      <rect
        x={toX}
        y={toY - 4}
        width={2}
        height={8}
        fill="var(--yak-color-primary)"
      />
    </g>
  );
}
