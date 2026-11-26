import type { TaskInstanceStatus } from "@/service/task-execution/types";
import type { WorkflowExecutionStatus } from "@/service/data-sync/workflow-state";

type ExecutionStatus = TaskInstanceStatus | WorkflowExecutionStatus | (string & {});

type StatusShape =
  | "circle-outline"
  | "diamond"
  | "circle"
  | "triangle-outline"
  | "diamond-alert"
  | "circle-check"
  | "diamond-check"
  | "triangle-alert"
  | "square-pause"
  | "square-x"
  | "diamond-outline"
  | "square-outline"
  | "square-stop"
  | "hexagon-outline"
  | "hexagon";

interface StatusVisual {
  shape: StatusShape;
  colorClassName: string;
  pulse?: boolean;
}

/**
 * Shared execution-state visuals for task instances and workflow instances.
 * Business state transitions remain owned by the backend enums.
 */
const STATUS_VISUALS: Record<string, StatusVisual> = {
  SUBMITTED_SUCCESS: {
    shape: "circle-outline",
    colorClassName: "text-[var(--yak-components-muted-text)]",
  },
  DISPATCH: {
    shape: "diamond",
    colorClassName: "text-[var(--yak-color-primary)]",
  },
  RUNNING_EXECUTION: {
    shape: "circle",
    colorClassName: "text-[var(--yak-color-primary)]",
    pulse: true,
  },
  DELAY_EXECUTION: {
    shape: "triangle-outline",
    colorClassName: "text-amber-600",
  },
  NEED_FAULT_TOLERANCE: {
    shape: "diamond-alert",
    colorClassName: "text-amber-600",
  },
  SUCCESS: {
    shape: "circle-check",
    colorClassName: "text-[var(--yak-color-success)]",
  },
  FORCED_SUCCESS: {
    shape: "diamond-check",
    colorClassName: "text-[var(--yak-color-success)]",
  },
  FAILURE: {
    shape: "triangle-alert",
    colorClassName: "text-[var(--yak-components-danger-text)]",
  },
  PAUSE: {
    shape: "square-pause",
    colorClassName: "text-[var(--yak-components-muted-text)]",
  },
  KILL: {
    shape: "square-x",
    colorClassName: "text-[var(--yak-components-muted-text)]",
  },
  READY_PAUSE: {
    shape: "diamond-outline",
    colorClassName: "text-amber-600",
  },
  READY_STOP: {
    shape: "square-outline",
    colorClassName: "text-amber-600",
  },
  STOP: {
    shape: "square-stop",
    colorClassName: "text-[var(--yak-components-muted-text)]",
  },
  SERIAL_WAIT: {
    shape: "hexagon-outline",
    colorClassName: "text-[var(--yak-components-muted-text)]",
  },
  FAILOVER: {
    shape: "hexagon",
    colorClassName: "text-amber-600",
  },
};

const STATUS_LABELS: Record<string, string> = {
  SUBMITTED_SUCCESS: "已提交",
  DISPATCH: "调度中",
  RUNNING_EXECUTION: "运行中",
  DELAY_EXECUTION: "延迟执行",
  NEED_FAULT_TOLERANCE: "故障恢复",
  SUCCESS: "成功",
  FORCED_SUCCESS: "强制成功",
  FAILURE: "失败",
  PAUSE: "已暂停",
  KILL: "已停止",
  READY_PAUSE: "暂停中",
  READY_STOP: "停止中",
  STOP: "已停止",
  SERIAL_WAIT: "等待中",
  FAILOVER: "恢复中",
};

function StatusGlyph({ shape }: { shape: StatusShape }) {
  switch (shape) {
    case "circle-outline":
      return (
        <circle
          cx="8"
          cy="8"
          r="5.25"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.75"
        />
      );
    case "diamond":
      return <path d="M8 1.75 14.25 8 8 14.25 1.75 8Z" fill="currentColor" />;
    case "circle":
      return <circle cx="8" cy="8" r="5.5" fill="currentColor" />;
    case "triangle-outline":
      return (
        <path
          d="M8 2 14.1 13H1.9L8 2Z"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.75"
          strokeLinejoin="round"
        />
      );
    case "diamond-alert":
      return (
        <>
          <path d="M8 1.75 14.25 8 8 14.25 1.75 8Z" fill="currentColor" />
          <path
            d="M8 5.1V8.6"
            stroke="white"
            strokeWidth="1.5"
            strokeLinecap="round"
          />
          <circle cx="8" cy="10.7" r="0.75" fill="white" />
        </>
      );
    case "circle-check":
      return (
        <>
          <circle cx="8" cy="8" r="6" fill="currentColor" />
          <path
            d="m4.8 8.1 2.1 2.1 4.3-4.4"
            fill="none"
            stroke="white"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </>
      );
    case "diamond-check":
      return (
        <>
          <path d="M8 1.5 14.5 8 8 14.5 1.5 8Z" fill="currentColor" />
          <path
            d="m4.9 8.1 2 2 4.2-4.3"
            fill="none"
            stroke="white"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </>
      );
    case "triangle-alert":
      return (
        <>
          <path d="M8 1.8 14.4 13H1.6L8 1.8Z" fill="currentColor" />
          <path
            d="M8 5.3v3.2"
            stroke="white"
            strokeWidth="1.5"
            strokeLinecap="round"
          />
          <circle cx="8" cy="10.7" r="0.7" fill="white" />
        </>
      );
    case "square-pause":
      return (
        <>
          <rect
            x="2.25"
            y="2.25"
            width="11.5"
            height="11.5"
            rx="2"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.75"
          />
          <path
            d="M6 5.3v5.4M10 5.3v5.4"
            stroke="currentColor"
            strokeWidth="1.7"
            strokeLinecap="round"
          />
        </>
      );
    case "square-x":
      return (
        <>
          <rect x="2.25" y="2.25" width="11.5" height="11.5" rx="2" fill="currentColor" />
          <path
            d="m5.5 5.5 5 5m0-5-5 5"
            stroke="white"
            strokeWidth="1.5"
            strokeLinecap="round"
          />
        </>
      );
    case "diamond-outline":
      return (
        <path
          d="M8 1.75 14.25 8 8 14.25 1.75 8Z"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.75"
          strokeLinejoin="round"
        />
      );
    case "square-outline":
      return (
        <rect
          x="2.25"
          y="2.25"
          width="11.5"
          height="11.5"
          rx="2"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.75"
        />
      );
    case "square-stop":
      return (
        <>
          <rect x="2.5" y="2.5" width="11" height="11" rx="2" fill="currentColor" />
          <path d="M5.5 8h5" stroke="white" strokeWidth="1.6" strokeLinecap="round" />
        </>
      );
    case "hexagon-outline":
      return (
        <path
          d="M5 2.1h6L14.3 8 11 13.9H5L1.7 8 5 2.1Z"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.75"
          strokeLinejoin="round"
        />
      );
    case "hexagon":
      return <path d="M5 2.1h6L14.3 8 11 13.9H5L1.7 8 5 2.1Z" fill="currentColor" />;
    default:
      return <circle cx="8" cy="8" r="5.25" fill="none" stroke="currentColor" strokeWidth="1.75" />;
  }
}

export interface ExecutionStatusIndicatorProps {
  status: ExecutionStatus;
  label?: string;
  size?: number;
  showLabel?: boolean;
  className?: string;
}

/** Shared geometric SVG status indicator for task and workflow execution states. */
export function ExecutionStatusIndicator({
  status,
  label,
  size = 14,
  showLabel = true,
  className = "",
}: ExecutionStatusIndicatorProps) {
  const visual = STATUS_VISUALS[status] ?? {
    shape: "circle-outline" as const,
    colorClassName: "text-[var(--yak-components-muted-text)]",
  };
  const statusLabel = label ?? STATUS_LABELS[status] ?? status;

  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap ${className}`}
      data-status={status}
      role={showLabel ? undefined : "img"}
      aria-label={showLabel ? undefined : statusLabel}
      title={statusLabel}
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 16 16"
        fill="none"
        aria-hidden="true"
        focusable="false"
        className={`shrink-0 ${visual.colorClassName} ${visual.pulse ? "motion-safe:animate-pulse" : ""}`}
      >
        <StatusGlyph shape={visual.shape} />
      </svg>
      {showLabel ? (
        <span className="text-xs text-[var(--yak-components-tab-text-hover)]">
          {statusLabel}
        </span>
      ) : null}
    </span>
  );
}
