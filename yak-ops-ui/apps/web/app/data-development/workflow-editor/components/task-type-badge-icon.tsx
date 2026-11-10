type TaskTypeBadgeSpec = {
  label: string;
  stroke: string;
  fill: string;
  text: string;
};

const DEFAULT_BADGE: TaskTypeBadgeSpec = {
  label: "TK",
  stroke: "#98a2b3",
  fill: "#f8fafc",
  text: "#667085",
};

const TASK_TYPE_BADGES: Record<string, TaskTypeBadgeSpec> = {
  CONDITIONS: {
    label: "IF",
    stroke: "#8b5cf6",
    fill: "#f5f3ff",
    text: "#7c3aed",
  },
  DEPENDENT: {
    label: "DP",
    stroke: "#8b5cf6",
    fill: "#f5f3ff",
    text: "#7c3aed",
  },
  SUB_WORKFLOW: {
    label: "SUB",
    stroke: "#8b5cf6",
    fill: "#f5f3ff",
    text: "#7c3aed",
  },
  SWITCH: {
    label: "SW",
    stroke: "#8b5cf6",
    fill: "#f5f3ff",
    text: "#7c3aed",
  },

  DATA_SYNC: {
    label: "DI",
    stroke: "#f59e0b",
    fill: "#fff7ed",
    text: "#ea580c",
  },
  DATAX: {
    label: "DX",
    stroke: "#f59e0b",
    fill: "#fff7ed",
    text: "#ea580c",
  },
  SEATUNNEL: {
    label: "ST",
    stroke: "#f59e0b",
    fill: "#fff7ed",
    text: "#ea580c",
  },
  SQOOP: {
    label: "SQ",
    stroke: "#f59e0b",
    fill: "#fff7ed",
    text: "#ea580c",
  },

  SQL: {
    label: "SQL",
    stroke: "#22c55e",
    fill: "#f0fdf4",
    text: "#16a34a",
  },
  PROCEDURE: {
    label: "PR",
    stroke: "#22c55e",
    fill: "#f0fdf4",
    text: "#16a34a",
  },
  JAVA: {
    label: "JV",
    stroke: "#22c55e",
    fill: "#f0fdf4",
    text: "#16a34a",
  },
  PYTHON: {
    label: "PY",
    stroke: "#22c55e",
    fill: "#f0fdf4",
    text: "#16a34a",
  },
  SPARK: {
    label: "SP",
    stroke: "#22c55e",
    fill: "#f0fdf4",
    text: "#16a34a",
  },
  MR: {
    label: "MR",
    stroke: "#22c55e",
    fill: "#f0fdf4",
    text: "#16a34a",
  },

  GRPC: {
    label: "GR",
    stroke: "#3b82f6",
    fill: "#eff6ff",
    text: "#2563eb",
  },
  HTTP: {
    label: "HT",
    stroke: "#3b82f6",
    fill: "#eff6ff",
    text: "#2563eb",
  },
  FLINK: {
    label: "FL",
    stroke: "#3b82f6",
    fill: "#eff6ff",
    text: "#2563eb",
  },
  FLINK_STREAM: {
    label: "FS",
    stroke: "#3b82f6",
    fill: "#eff6ff",
    text: "#2563eb",
  },
  DINKY: {
    label: "DK",
    stroke: "#3b82f6",
    fill: "#eff6ff",
    text: "#2563eb",
  },
  K8S: {
    label: "K8",
    stroke: "#3b82f6",
    fill: "#eff6ff",
    text: "#2563eb",
  },

  SHELL: {
    label: "SH",
    stroke: "#98a2b3",
    fill: "#f8fafc",
    text: "#667085",
  },
  REMOTESHELL: {
    label: "RS",
    stroke: "#98a2b3",
    fill: "#f8fafc",
    text: "#667085",
  },
  HIVECLI: {
    label: "HC",
    stroke: "#98a2b3",
    fill: "#f8fafc",
    text: "#667085",
  },

  EMR: {
    label: "EM",
    stroke: "#14b8a6",
    fill: "#f0fdfa",
    text: "#0f766e",
  },
  EMR_SERVERLESS: {
    label: "ES",
    stroke: "#14b8a6",
    fill: "#f0fdfa",
    text: "#0f766e",
  },
  DMS: {
    label: "DM",
    stroke: "#14b8a6",
    fill: "#f0fdfa",
    text: "#0f766e",
  },
  DATA_FACTORY: {
    label: "DF",
    stroke: "#14b8a6",
    fill: "#f0fdfa",
    text: "#0f766e",
  },
  ALIYUN_SERVERLESS_SPARK: {
    label: "AS",
    stroke: "#14b8a6",
    fill: "#f0fdfa",
    text: "#0f766e",
  },

  JUPYTER: {
    label: "NB",
    stroke: "#f97316",
    fill: "#fff7ed",
    text: "#ea580c",
  },
  MLFLOW: {
    label: "ML",
    stroke: "#ec4899",
    fill: "#fdf2f8",
    text: "#db2777",
  },
  OPENMLDB: {
    label: "OM",
    stroke: "#ec4899",
    fill: "#fdf2f8",
    text: "#db2777",
  },
  DVC: {
    label: "DV",
    stroke: "#ec4899",
    fill: "#fdf2f8",
    text: "#db2777",
  },
  SAGEMAKER: {
    label: "SM",
    stroke: "#ec4899",
    fill: "#fdf2f8",
    text: "#db2777",
  },
  KUBEFLOW: {
    label: "KF",
    stroke: "#ec4899",
    fill: "#fdf2f8",
    text: "#db2777",
  },
};

function fallbackBadge(taskType: string): TaskTypeBadgeSpec {
  const normalized = taskType.trim().toUpperCase();
  const compact = normalized.replace(/[^A-Z0-9]/g, "");
  const label =
    compact.length >= 2
      ? compact.slice(0, Math.min(3, compact.length))
      : DEFAULT_BADGE.label;

  return {
    ...DEFAULT_BADGE,
    label,
  };
}

export function TaskTypeBadgeIcon({
  taskType,
  size = 16,
}: {
  taskType: string;
  size?: number;
}) {
  const normalized = taskType.trim().toUpperCase();
  const spec = TASK_TYPE_BADGES[normalized] ?? fallbackBadge(normalized);
  const fontSize =
    spec.label.length >= 3 ? 5.1 : 5.9;

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 16 16"
      aria-hidden="true"
      className="block shrink-0"
    >
      <rect
        x="1.25"
        y="1.25"
        width="13.5"
        height="13.5"
        rx="1.3"
        fill={spec.fill}
        stroke={spec.stroke}
        strokeWidth="1"
      />
      <text
        x="8"
        y="8.45"
        textAnchor="middle"
        dominantBaseline="middle"
        fontSize={fontSize}
        fontWeight="700"
        letterSpacing="-0.15"
        fill={spec.text}
        style={{
          fontFamily:
            'Arial, "PingFang SC", "Microsoft YaHei", sans-serif',
          userSelect: "none",
        }}
      >
        {spec.label}
      </text>
    </svg>
  );
}
