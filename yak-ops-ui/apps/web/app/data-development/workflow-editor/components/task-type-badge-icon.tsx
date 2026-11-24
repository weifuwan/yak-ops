import {
  Activity,
  ArrowLeftRight,
  BookOpen,
  Boxes,
  Brain,
  Cloud,
  Code2,
  Coffee,
  Database,
  GitBranch,
  Globe,
  Layers,
  Server,
  Terminal,
  Workflow,
  Zap,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

type TaskTypeIconSpec = {
  icon: LucideIcon;
  background: string;
};

const DEFAULT_ICON: TaskTypeIconSpec = {
  icon: Workflow,
  background: "#64748b",
};

// Keep the existing task-type color families, but render them with Dify's
// solid-color icon container and white glyph instead of text badges.
const TASK_TYPE_ICON_MAP: Record<string, TaskTypeIconSpec> = {
  CONDITIONS: { icon: GitBranch, background: "var(--yak-color-primary)" },
  DEPENDENT: { icon: GitBranch, background: "#8b5cf6" },
  SUB_WORKFLOW: { icon: Workflow, background: "#8b5cf6" },
  SWITCH: { icon: GitBranch, background: "#8b5cf6" },

  DATA_SYNC: { icon: ArrowLeftRight, background: "#f59e0b" },
  DATAX: { icon: ArrowLeftRight, background: "#f59e0b" },
  SEATUNNEL: { icon: ArrowLeftRight, background: "#f59e0b" },
  SQOOP: { icon: ArrowLeftRight, background: "#f59e0b" },

  SQL: { icon: Database, background: "#22c55e" },
  PROCEDURE: { icon: Code2, background: "#22c55e" },
  JAVA: { icon: Coffee, background: "#22c55e" },
  PYTHON: { icon: Code2, background: "#22c55e" },
  SPARK: { icon: Zap, background: "#22c55e" },
  MR: { icon: Layers, background: "#22c55e" },

  GRPC: { icon: Globe, background: "#3b82f6" },
  HTTP: { icon: Globe, background: "#3b82f6" },
  FLINK: { icon: Activity, background: "#3b82f6" },
  FLINK_STREAM: { icon: Activity, background: "#3b82f6" },
  DINKY: { icon: Server, background: "#3b82f6" },
  K8S: { icon: Boxes, background: "#3b82f6" },

  SHELL: { icon: Terminal, background: "#64748b" },
  REMOTESHELL: { icon: Terminal, background: "#64748b" },
  HIVECLI: { icon: Terminal, background: "#64748b" },

  EMR: { icon: Cloud, background: "#14b8a6" },
  EMR_SERVERLESS: { icon: Cloud, background: "#14b8a6" },
  DMS: { icon: Database, background: "#14b8a6" },
  DATA_FACTORY: { icon: Workflow, background: "#14b8a6" },
  ALIYUN_SERVERLESS_SPARK: { icon: Cloud, background: "#14b8a6" },

  JUPYTER: { icon: BookOpen, background: "#f97316" },
  MLFLOW: { icon: Brain, background: "#ec4899" },
  OPENMLDB: { icon: Database, background: "#ec4899" },
  DVC: { icon: Layers, background: "#ec4899" },
  SAGEMAKER: { icon: Brain, background: "#ec4899" },
  KUBEFLOW: { icon: Workflow, background: "#ec4899" },
};

export function TaskTypeBadgeIcon({
  taskType,
  size = 16,
}: {
  taskType: string;
  size?: number;
}) {
  const normalized = taskType.trim().toUpperCase();
  const spec = TASK_TYPE_ICON_MAP[normalized] ?? DEFAULT_ICON;
  const Icon = spec.icon;
  const iconSize =
    size >= 24 ? 16 : size >= 20 ? 14 : Math.max(10, Math.round(size * 0.75));
  const radius = size >= 24 ? 8 : size >= 20 ? 6 : 5;

  return (
    <span
      aria-hidden="true"
      className="inline-flex shrink-0 items-center justify-center border-[0.5px] border-white/10 text-white"
      style={{
        width: size,
        height: size,
        borderRadius: radius,
        backgroundColor: spec.background,
        boxShadow:
          size >= 24
            ? "0 2px 5px rgba(16, 24, 40, 0.16)"
            : "0 1px 2px rgba(16, 24, 40, 0.14)",
      }}
    >
      {normalized === "CONDITIONS" ? (
        <svg
          aria-hidden="true"
          width={iconSize}
          height={iconSize}
          viewBox="0 0 1024 1024"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            d="M597.358 218.697a47.982 47.982 0 0 1 0-96.036h213.284c26.551 0 48.055 21.504 48.055 47.981V384a47.982 47.982 0 0 1-96.036 0v-97.426L537.234 512l225.427 225.426V640a47.982 47.982 0 1 1 96.036 0v213.358a47.982 47.982 0 0 1-48.055 47.981H597.358a47.982 47.982 0 0 1 0-95.963h97.426L449.463 559.982H128a47.982 47.982 0 1 1 0-95.964h321.463l245.321-245.32h-97.426z"
            fill="currentColor"
          />
        </svg>
      ) : (
        <Icon size={iconSize} strokeWidth={2.1} aria-hidden="true" />
      )}
    </span>
  );
}
