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
  JAVA: { icon: Coffee, background: "var(--yak-color-primary)" },
  PYTHON: { icon: Code2, background: "#22c55e" },
  SPARK: { icon: Zap, background: "#22c55e" },
  MR: { icon: Layers, background: "#22c55e" },

  GRPC: { icon: Globe, background: "#3b82f6" },
  HTTP: { icon: Globe, background: "var(--yak-color-primary)" },
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
      {normalized === "JAVA" ? (
        <svg
          aria-hidden="true"
          width={iconSize}
          height={iconSize}
          viewBox="0 0 1024 1024"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            d="M377.642667 791.893333s-39.125333 22.784 27.861333 30.464c81.152 9.301333 122.624 7.978667 212.010667-9.002666 0 0 23.552 14.762667 56.362666 27.562666-200.490667 85.888-453.674667-5.034667-296.234666-49.024m-24.533334-112.085333s-43.861333 32.469333 23.125334 39.424c86.698667 8.917333 155.136 9.685333 273.621333-13.141333 0 0 16.384 16.597333 42.112 25.685333-242.304 70.869333-512.298667 5.546667-338.858667-51.968m206.506667-190.208c49.408 56.874667-12.970667 108.074667-12.970667 108.074667s125.397333-64.768 67.797334-145.834667c-53.802667-75.605333-95.061333-113.152 128.298666-242.688 0-0.042667-350.549333 87.509333-183.125333 280.448m265.130667 385.237333s28.970667 23.850667-31.872 42.282667c-115.712 35.072-481.621333 45.610667-583.210667 1.408-36.522667-15.914667 32-37.973333 53.504-42.581333 22.485333-4.864 35.328-3.968 35.328-3.968-40.661333-28.629333-262.656 56.192-112.768 80.512 408.746667 66.261333 745.045333-29.866667 639.018667-77.653334M396.458667 563.626667s-186.112 44.202667-65.877334 60.245333c50.730667 6.784 151.936 5.248 246.186667-2.645333 77.056-6.485333 154.368-20.352 154.368-20.352s-27.178667 11.605333-46.848 25.045333c-188.970667 49.706667-554.069333 26.581333-448.938667-24.234667 88.832-42.922667 161.109333-38.058667 161.109334-38.058666m333.824 186.624c192.128-99.84 103.296-195.797333 41.301333-182.826667-15.146667 3.157333-21.973333 5.888-21.973333 5.888s5.632-8.832 16.426666-12.672c122.666667-43.136 217.002667 127.189333-39.594666 194.645333 0-0.042667 2.986667-2.645333 3.84-5.034666M614.442667 0s106.410667 106.410667-100.906667 270.08c-166.229333 131.285333-37.888 206.165333-0.042667 291.669333-97.024-87.594667-168.234667-164.608-120.490666-236.330666 70.144-105.344 264.405333-156.373333 221.44-325.418667M415.317333 1020.757333c184.405333 11.818667 467.584-6.528 474.282667-93.781333 0 0-12.885333 33.066667-152.405333 59.349333-157.354667 29.610667-351.530667 26.154667-466.645334 7.168 0-0.042667 23.594667 19.498667 144.768 27.264"
            fill="currentColor"
          />
        </svg>
      ) : normalized === "HTTP" ? (
        <svg
          aria-hidden="true"
          width={iconSize}
          height={iconSize}
          viewBox="0 0 1024 1024"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            d="M192 469.333h-85.333V384h-64v256h64V533.333H192V640h64V384h-64v85.333zM298.667 448h64v192h64V448h64v-64h-192v64z m234.666 0h64v192h64V448h64v-64h-192v64z m384-64H768v256h64v-85.333h85.333c36.267 0 64-27.734 64-64V448c0-36.267-27.733-64-64-64z m0 106.667H832V448h85.333v42.667z"
            fill="currentColor"
          />
        </svg>
      ) : normalized === "CONDITIONS" ? (
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
