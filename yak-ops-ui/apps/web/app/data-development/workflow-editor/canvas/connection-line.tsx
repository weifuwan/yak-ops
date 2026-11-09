import {
  getBezierPath,
  type ConnectionLineComponentProps,
} from "@xyflow/react";

export function WorkflowConnectionLine({
  fromX,
  fromY,
  toX,
  toY,
  fromPosition,
  toPosition,
}: ConnectionLineComponentProps) {
  const [path] = getBezierPath({
    sourceX: fromX,
    sourceY: fromY,
    sourcePosition: fromPosition,
    targetX: toX,
    targetY: toY,
    targetPosition: toPosition,
    curvature: 0.18,
  });

  return (
    <path
      d={path}
      fill="none"
      stroke="var(--yak-color-primary)"
      strokeWidth={1.5}
      strokeDasharray="5 4"
    />
  );
}
