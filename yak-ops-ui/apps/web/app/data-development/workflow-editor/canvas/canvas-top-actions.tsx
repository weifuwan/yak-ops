import { Activity, Code2, List } from "lucide-react";

import type { CanvasPanelKey } from "./canvas-side-panel";

const ACTIONS = [
  { key: "nodes", label: "显示内部节点列表", Icon: List },
  { key: "spec", label: "显示 Spec", Icon: Code2 },
  { key: "operations", label: "运维中心", Icon: Activity },
] as const;

export function CanvasTopActions({
  activePanel,
  onSelect,
}: {
  activePanel: CanvasPanelKey | null;
  onSelect: (panel: CanvasPanelKey) => void;
}) {
  return (
    <div className="pointer-events-none absolute right-3 top-3 z-30 max-w-[calc(100%-24px)]">
      <div className="pointer-events-auto flex max-w-full items-center gap-1 overflow-x-auto rounded-lg border border-[#dfe3e8] bg-white/95 p-1 shadow-sm backdrop-blur">
        {ACTIONS.map(({ key, label, Icon }) => {
          const isActive = activePanel === key;

          return (
            <button
              key={key}
              type="button"
              title={label}
              aria-label={label}
              aria-expanded={isActive}
              aria-controls="workflow-canvas-side-panel"
              data-active={isActive ? "true" : undefined}
              onClick={() => onSelect(key)}
              className="flex h-8 shrink-0 cursor-pointer items-center gap-1.5 rounded-md px-2 text-xs text-[#475467] transition-colors hover:bg-[#f3f5f8] hover:text-[#303846] data-[active=true]:bg-[#eef3ff] data-[active=true]:text-[#315efb]"
            >
              <Icon size={14} strokeWidth={1.8} />
              {label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
