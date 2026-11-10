import {
  getBezierPath,
  Position,
  type ConnectionLineComponentProps,
} from "reactflow";

export function WorkflowConnectionLine({
  fromX,
  fromY,
  toX,
  toY,
}: ConnectionLineComponentProps) {
  const [path] = getBezierPath({
    sourceX: fromX,
    sourceY: fromY,
    sourcePosition: Position.Right,
    targetX: toX,
    targetY: toY,
    targetPosition: Position.Left,
    curvature: 0.16,
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
