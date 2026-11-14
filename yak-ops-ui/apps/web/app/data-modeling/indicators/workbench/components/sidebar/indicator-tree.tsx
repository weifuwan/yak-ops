import type { MouseEvent as ReactMouseEvent } from "react";
import { ChartNoAxesCombined, ChevronDown, ChevronRight, FileText, Folder } from "lucide-react";

import { PlanningIcon } from "../../../../planning/icons";
import type { IndicatorTreeNode } from "../../types";

type TreeRowsProps = {
  nodes: IndicatorTreeNode[];
  depth: number;
  expandedKeys: Set<string>;
  selectedKey: string;
  onToggle: (key: string) => void;
  onSelect: (record: ModelingRecord) => void;
  onContextMenu: (event: ReactMouseEvent<HTMLButtonElement>, node: IndicatorTreeNode) => void;
};

export function IndicatorTreeRows({
  nodes,
  depth,
  expandedKeys,
  selectedKey,
  onToggle,
  onSelect,
  onContextMenu,
}: TreeRowsProps) {
  return (
    <div className="space-y-0.5">
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
                "group flex min-h-8 w-full items-center gap-1.5 rounded-md px-2 text-left text-xs transition-colors",
                selected
                  ? "bg-[var(--yak-color-primary-soft)] text-[var(--yak-color-primary)]"
                  : "text-[var(--yak-components-panel-text)] hover:bg-[var(--yak-color-hover)]",
              ].join(" ")}
              style={{ paddingLeft: 8 + depth * 14 }}
            >
              <span className="flex size-4 shrink-0 items-center justify-center text-[var(--yak-components-muted-text)]">
                {node.kind === "group" && hasChildren
                  ? expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />
                  : null}
              </span>
              {node.nodeType === "root" ? (
                <ChartNoAxesCombined size={14} className="shrink-0 text-[var(--yak-color-primary)]" />
              ) : node.nodeType === "businessType" ? (
                <PlanningIcon name="business-type" size={14} className={selected ? "text-[var(--yak-color-primary)]" : "text-[var(--yak-components-muted-text)]"} />
              ) : node.nodeType === "dataDomain" ? (
                <PlanningIcon name="data-domain" size={14} className={selected ? "text-[var(--yak-color-primary)]" : "text-[var(--yak-components-muted-text)]"} />
              ) : node.nodeType === "businessProcess" ? (
                <PlanningIcon name="business-process" size={14} className={selected ? "text-[var(--yak-color-primary)]" : "text-[var(--yak-components-muted-text)]"} />
              ) : node.kind === "group" ? (
                <Folder size={14} className="shrink-0 text-[var(--yak-components-muted-text)]" />
              ) : (
                <FileText size={14} className="shrink-0 text-[var(--yak-color-primary)]" />
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
              <div className="mt-0.5">
                <IndicatorTreeRows
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
