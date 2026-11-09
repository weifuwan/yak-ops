import {
  Boxes,
  GitBranch,
  Send,
  TimerReset,
  type LucideIcon,
} from "lucide-react";

export type DataDevelopmentSection =
  | "development"
  | "schedule"
  | "resource"
  | "publish";

type RailItem = {
  id: DataDevelopmentSection;
  label: string;
  icon: LucideIcon;
};

const RAIL_ITEMS: RailItem[] = [
  {
    id: "development",
    label: "开发",
    icon: GitBranch,
  },
  {
    id: "schedule",
    label: "调度",
    icon: TimerReset,
  },
  {
    id: "resource",
    label: "资源",
    icon: Boxes,
  },
  {
    id: "publish",
    label: "发布",
    icon: Send,
  },
];

type DevelopmentRailProps = {
  activeSection: DataDevelopmentSection;
  onSectionChange: (section: DataDevelopmentSection) => void;
};

export function DevelopmentRail({
  activeSection,
  onSectionChange,
}: DevelopmentRailProps) {
  return (
    <aside
      aria-label="数据开发工具栏"
      className="flex w-11 shrink-0 flex-col items-center border-r border-[var(--yak-components-border)] bg-[var(--yak-components-panel-bg)] py-1"
    >
      {RAIL_ITEMS.map((item) => {
        const Icon = item.icon;
        const active = item.id === activeSection;

        return (
          <button
            key={item.id}
            type="button"
            title={item.label}
            aria-label={item.label}
            aria-pressed={active}
            onClick={() => onSectionChange(item.id)}
            className={[
              "relative flex size-10 items-center justify-center transition-colors",
              active
                ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
                : "text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)]",
            ].join(" ")}
          >
            {active ? (
              <span className="absolute inset-y-1 left-0 w-0.5 rounded-r bg-[var(--yak-color-primary)]" />
            ) : null}
            <Icon size={17} strokeWidth={1.8} aria-hidden="true" />
          </button>
        );
      })}
    </aside>
  );
}
