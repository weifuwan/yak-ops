import {
  Archive,
  Bot,
  Box,
  CircleHelp,
  Ear,
  FileText,
  History,
  PanelLeftClose,
  Puzzle,
  Server,
  Settings,
  Sparkles,
  SquareCheckBig,
  SquareCode,
  Stamp,
  Sun,
  Table2,
  Trash2,
  type LucideIcon,
} from "lucide-react";

export type DataDevelopmentSection =
  | "development"
  | "manual"
  | "component"
  | "catalog"
  | "resource"
  | "ai-processing"
  | "history"
  | "review"
  | "operation-check"
  | "smoke-test"
  | "code-management"
  | "governance"
  | "recycle-bin";

type RailItem = {
  id: DataDevelopmentSection;
  label: string;
  icon: LucideIcon;
};

const RAIL_ITEMS: RailItem[] = [
  { id: "development", label: "数据开发", icon: SquareCode },
  { id: "manual", label: "手动", icon: Ear },
  { id: "component", label: "组件管理", icon: Puzzle },
  { id: "catalog", label: "数据目录", icon: Table2 },
  { id: "resource", label: "资源管理", icon: Archive },
  { id: "ai-processing", label: "AI 数据处理", icon: Bot },
  { id: "history", label: "运行历史", icon: History },
  { id: "review", label: "代码评审列表", icon: Stamp },
  { id: "operation-check", label: "操作检查", icon: SquareCheckBig },
  { id: "smoke-test", label: "冒烟测试记录", icon: FileText },
  { id: "code-management", label: "代码管理", icon: Server },
  { id: "governance", label: "数据资产治理", icon: Box },
  { id: "recycle-bin", label: "回收站", icon: Trash2 },
];

type DevelopmentRailProps = {
  activeSection: DataDevelopmentSection;
  onSectionChange: (section: DataDevelopmentSection) => void;
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
      className="flex h-9 w-11 shrink-0 items-center justify-center text-[#4e535a] outline-none"
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
  onSectionChange,
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
        <RailButton label="收起活动栏" icon={PanelLeftClose} />
      </div>
    </aside>
  );
}
