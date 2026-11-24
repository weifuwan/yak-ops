import {
  Archive,
  CircleHelp,
  CalendarClock,
  GitBranch,
  ListChecks,
  SlidersHorizontal,
  Ear,
  History,
  PanelLeftClose,
  PanelLeftOpen,
  Settings,
  Sparkles,
  SquareCheckBig,
  SquareCode,
  Sun,
  Table2,
  type LucideIcon,
} from "lucide-react";

export type DataDevelopmentSection =
  | "development"
  | "manual"
  | "backfill"
  | "lineage"
  | "task-instances"
  | "parameters"
  | "catalog"
  | "resource"
  | "history"
  | "operation-check";

type RailItem = {
  id: DataDevelopmentSection;
  label: string;
  icon: LucideIcon;
};

const RAIL_ITEMS: RailItem[] = [
  { id: "development", label: "数据开发", icon: SquareCode },
  { id: "manual", label: "手动运行", icon: Ear },
  { id: "backfill", label: "补数中心", icon: CalendarClock },
  { id: "lineage", label: "依赖分析", icon: GitBranch },
  { id: "task-instances", label: "任务运行记录", icon: ListChecks },
  { id: "parameters", label: "项目参数", icon: SlidersHorizontal },
  { id: "catalog", label: "数据目录", icon: Table2 },
  { id: "resource", label: "资源管理", icon: Archive },
  { id: "history", label: "运行历史", icon: History },
  { id: "operation-check", label: "操作历史", icon: SquareCheckBig },
];

type DevelopmentRailProps = {
  activeSection: DataDevelopmentSection;
  panelCollapsed: boolean;
  onSectionChange: (section: DataDevelopmentSection) => void;
  onOpenAgent: () => void;
  onTogglePanel: () => void;
};

function RailButton({
  label,
  icon: Icon,
  active = false,
  indicator = false,
  onClick,
}: {
  label: string;
  icon: LucideIcon;
  active?: boolean;
  indicator?: boolean;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      aria-pressed={active || undefined}
      onClick={onClick}
      className="flex h-9 w-11 shrink-0 cursor-pointer items-center justify-center text-[#4e535a] outline-none"
    >
      <span
        className={[
          "relative flex size-8 items-center justify-center rounded-[2px] transition-colors",
          active
            ? "bg-[#cbd7fc] text-[rgba(0,51,204,0.9)]"
            : "hover:bg-[#f0f0f0] hover:text-[#30343a]",
        ].join(" ")}
      >
        <Icon size={16} strokeWidth={1.45} aria-hidden="true" />
        {indicator ? (
          <span className="absolute right-[3px] top-[3px] size-[5px] rounded-full border border-[#fafafa] bg-[#1f63ff]" />
        ) : null}
      </span>
    </button>
  );
}

export function DevelopmentRail({
  activeSection,
  panelCollapsed,
  onSectionChange,
  onOpenAgent,
  onTogglePanel,
}: DevelopmentRailProps) {
  return (
    <aside
      aria-label="数据开发活动栏"
      className="flex w-11 shrink-0 flex-col items-center border-r border-[#f2f2f2] bg-[#fafafa]"
    >
      <RailButton
        label="Data Agent · 数据开发助手"
        icon={Sparkles}
        indicator
        onClick={onOpenAgent}
      />

      <nav
        aria-label="活动视图切换器"
        className="flex w-full flex-col items-center"
      >
        {RAIL_ITEMS.map((item) => (
          <RailButton
            key={item.id}
            label={item.label}
            icon={item.icon}
            active={item.id === activeSection}
            onClick={() => onSectionChange(item.id)}
          />
        ))}
      </nav>

      <div className="mt-auto flex w-full flex-col items-center">
        <RailButton label="查看帮助" icon={CircleHelp} />
        <RailButton label="切换主题" icon={Sun} />
        <RailButton label="管理" icon={Settings} />
        <RailButton
          label={panelCollapsed ? "展开侧边面板" : "收起侧边面板"}
          icon={panelCollapsed ? PanelLeftOpen : PanelLeftClose}
          onClick={onTogglePanel}
        />
      </div>
    </aside>
  );
}
