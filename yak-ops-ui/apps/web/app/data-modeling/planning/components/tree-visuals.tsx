import type { MouseEvent as ReactMouseEvent, ReactNode } from "react";
import { Skeleton } from "@yak-ops/yak-ui";

import { PlanningIcon, type PlanningIconName } from "./icons";

type PlanningTreeRowProps = {
  title: string;
  icon: PlanningIconName;
  depth: number;
  selected: boolean;
  hasChildren: boolean;
  expanded: boolean;
  suffix?: ReactNode;
  onToggle: () => void;
  onSelect: () => void;
  onContextMenu: (event: ReactMouseEvent) => void;
};

export function PlanningTreeArrow({ expanded }: { expanded: boolean }) {
  return (
    <svg
      viewBox="0 0 1024 1024"
      width="8"
      height="8"
      aria-hidden="true"
      fill="currentColor"
      className={[
        "shrink-0 transition-transform duration-150 ease-out motion-reduce:transition-none",
        expanded ? "rotate-90" : "rotate-0",
      ].join(" ")}
    >
      <path d="M215.578947 0v1024l592.842106-512L215.578947 0z" />
    </svg>
  );
}

/**
 * Shared planning tree row. Its spacing, typography, selection and divider
 * follow the indicator tree's visual contract without coupling the modules.
 */
export function PlanningTreeRow({
  title,
  icon,
  depth,
  selected,
  hasChildren,
  expanded,
  suffix,
  onToggle,
  onSelect,
  onContextMenu,
}: PlanningTreeRowProps) {
  return (
    <div
      role="treeitem"
      aria-selected={selected}
      aria-expanded={hasChildren ? expanded : undefined}
      title={title}
      onContextMenu={onContextMenu}
      onClick={onSelect}
      className={[
        "group flex min-h-8 w-full items-center gap-1 rounded-none px-0 text-left text-xs leading-6 transition-colors",
        selected
          ? "bg-[#DFE7FF] text-[var(--yak-components-panel-text)]"
          : "text-[var(--yak-components-panel-text)] hover:bg-[var(--yak-color-hover)]",
      ].join(" ")}
      style={{ paddingLeft: depth * 24, boxShadow: "0 0 0 0.5px #eceff780" }}
    >
      <button
        type="button"
        className="flex size-4 shrink-0 cursor-pointer items-center justify-center text-[var(--yak-components-muted-text)]"
        aria-label={hasChildren ? (expanded ? "收起" : "展开") : "无子级"}
        onClick={(event) => {
          event.stopPropagation();
          if (hasChildren) onToggle();
        }}
      >
        {hasChildren ? <PlanningTreeArrow expanded={expanded} /> : null}
      </button>
      <PlanningIcon name={icon} size={14} className="text-[var(--yak-components-muted-text)]" />
      <span className="min-w-0 flex-1 truncate">{title}</span>
      {suffix}
    </div>
  );
}

export function PlanningTreeSkeleton() {
  const rows = [
    { depth: 0, width: 92 },
    { depth: 1, width: 116 },
    { depth: 1, width: 84 },
    { depth: 2, width: 76 },
    { depth: 2, width: 104 },
    { depth: 1, width: 112 },
  ];

  return (
    <div role="status" aria-label="正在加载目录" className="flex flex-col gap-1 px-2 py-2">
      <span className="sr-only">正在加载目录...</span>
      {rows.map((row, index) => (
        <div
          key={index}
          className="flex min-h-8 items-center gap-1"
          style={{ paddingLeft: row.depth * 24 }}
        >
          <Skeleton width={8} height={8} className="rounded-[2px]" />
          <Skeleton width={14} height={14} className="rounded-sm" />
          <Skeleton width={row.width} height={10} />
        </div>
      ))}
    </div>
  );
}
