import type { MouseEvent as ReactMouseEvent } from "react";
import { PlanningIcon } from "../../../../planning/icons";
import type { ModelingRecord } from "../../../../service";
import { IndicatorIcon, getIndicatorIconName } from "../../../icons";
import type { IndicatorKind, IndicatorTreeNode } from "../../types";

function ExpandArrowIcon({ expanded }: { expanded: boolean }) {
  return (
    <svg
      viewBox="0 0 1024 1024"
      width="10"
      height="10"
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

function FolderIcon({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 1024 1024"
      width="14"
      height="14"
      aria-hidden="true"
      className={className}
      fill="currentColor"
    >
      <path d="M42.688 106.688H404.48l85.376 106.624h491.52V896H42.624V106.688zM128 192v181.312h768V298.688H448.832L363.52 192H128z m768 266.688H128v352h768v-352z" />
    </svg>
  );
}

type TreeRowsProps = {
  activeKind: IndicatorKind;
  nodes: IndicatorTreeNode[];
  depth: number;
  expandedKeys: Set<string>;
  selectedKey: string;
  onToggle: (key: string) => void;
  onSelect: (record: ModelingRecord) => void;
  onContextMenu: (event: ReactMouseEvent<HTMLButtonElement>, node: IndicatorTreeNode) => void;
};

export function IndicatorTreeRows({
  activeKind,
  nodes,
  depth,
  expandedKeys,
  selectedKey,
  onToggle,
  onSelect,
  onContextMenu,
}: TreeRowsProps) {
  return (
    <div className="flex flex-col">
      {nodes.map((node) => {
        const hasChildren = node.children.length > 0;
        const expanded = expandedKeys.has(node.key);
        const selected = selectedKey === node.key;
        return (
          <div key={node.key}>
            <button
              type="button"
              title={node.title}
              onClick={() => {
                if (node.kind === "asset" && node.record) onSelect(node.record);
                else onToggle(node.key);
              }}
              onContextMenu={(event) => {
                if (node.kind === "asset") onContextMenu(event, node);
              }}
              className={[
                "group flex min-h-8 w-full items-center gap-1 rounded-none px-0 text-left text-xs leading-6 transition-colors",
                selected
                  ? "bg-[#DFE7FF] text-[var(--yak-components-panel-text)]"
                  : "text-[var(--yak-components-panel-text)] hover:bg-[var(--yak-color-hover)]",
              ].join(" ")}
              style={{ paddingLeft: depth * 24, boxShadow: "0 0 0 0.5px #eceff780" }}
            >
              <span className="flex size-4 shrink-0 items-center justify-center text-[var(--yak-components-muted-text)]">
                {node.kind === "group" && hasChildren
                  ? <ExpandArrowIcon expanded={expanded} />
                  : null}
              </span>
              {node.nodeType === "root" ? (
                <IndicatorIcon name={getIndicatorIconName(activeKind)} size={14} />
              ) : node.nodeType === "businessType" ? (
                <PlanningIcon name="business-type" size={14} className="text-[var(--yak-components-muted-text)]" />
              ) : node.nodeType === "dataDomain" ? (
                <PlanningIcon name="data-domain" size={14} className="text-[var(--yak-components-muted-text)]" />
              ) : node.nodeType === "businessProcess" ? (
                <PlanningIcon name="business-process" size={14} className="text-[var(--yak-components-muted-text)]" />
              ) : node.kind === "asset" ? (
                <IndicatorIcon name={getIndicatorIconName(activeKind)} size={14} />
              ) : (
                <FolderIcon className="shrink-0 text-[var(--yak-components-muted-text)]" />
              )}
              <span className="min-w-0 flex-1 truncate">{node.title}</span>
              {typeof node.count === "number" ? (
                <span className="shrink-0 text-[11px] tabular-nums text-[var(--yak-components-muted-text)]">({node.count})</span>
              ) : null}
              {node.kind === "asset" && node.record?.submit === true ? (
                <span className="shrink-0 rounded bg-[var(--yak-color-hover)] px-1.5 py-0.5 text-[10px] text-[var(--yak-components-muted-text)]">已提交</span>
              ) : null}
            </button>
            {hasChildren && expanded ? (
              <div>
                <IndicatorTreeRows
                  activeKind={activeKind}
                  nodes={node.children}
                  depth={depth + 1}
                  expandedKeys={expandedKeys}
                  selectedKey={selectedKey}
                  onToggle={onToggle}
                  onSelect={onSelect}
                  onContextMenu={onContextMenu}
                />
              </div>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}
