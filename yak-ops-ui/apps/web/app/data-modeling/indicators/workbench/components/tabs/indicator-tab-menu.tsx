import { createPortal } from "react-dom";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@yak-ops/yak-ui";
import { ArrowLeftToLine, ArrowRightToLine, X, XCircle } from "lucide-react";

import type { IndicatorTab, IndicatorTabContextMenuState } from "../../types";

type CloseAction = "current" | "other" | "left" | "right" | "all";
type Props = {
  contextMenu: IndicatorTabContextMenuState | null;
  portalRoot: HTMLElement | null;
  contextTab: IndicatorTab | null;
  contextTabIndex: number;
  tabs: IndicatorTab[];
  onCloseMenu: () => void;
  onCloseTabs: (keys: string[]) => void;
  getTabKeysToClose: (anchorKey: string, action: CloseAction) => string[];
};

export function IndicatorTabMenu({ contextMenu, portalRoot, contextTab, contextTabIndex, tabs, onCloseMenu, onCloseTabs, getTabKeysToClose }: Props) {
  if (!contextMenu || !portalRoot || !contextTab) return null;
  return createPortal(
    <DropdownMenu open onOpenChange={(open) => { if (!open) onCloseMenu(); }}>
      <DropdownMenuTrigger aria-hidden="true" tabIndex={-1} className="pointer-events-none fixed size-px opacity-0" style={{ left: contextMenu.x, top: contextMenu.y }} />
      <DropdownMenuContent side="bottom" align="start" sideOffset={0} className="w-[208px] rounded-md p-1">
        <DropdownMenuItem onClick={() => onCloseTabs([contextTab.key])}><X size={14} />关闭</DropdownMenuItem>
        <DropdownMenuItem disabled={tabs.length < 2} onClick={() => onCloseTabs(getTabKeysToClose(contextTab.key, "other"))}><XCircle size={14} />关闭其他标签页</DropdownMenuItem>
        <DropdownMenuItem disabled={contextTabIndex < 1} onClick={() => onCloseTabs(getTabKeysToClose(contextTab.key, "left"))}><ArrowLeftToLine size={14} />关闭左侧标签页</DropdownMenuItem>
        <DropdownMenuItem disabled={contextTabIndex < 0 || contextTabIndex >= tabs.length - 1} onClick={() => onCloseTabs(getTabKeysToClose(contextTab.key, "right"))}><ArrowRightToLine size={14} />关闭右侧标签页</DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem tone="danger" onClick={() => onCloseTabs(getTabKeysToClose(contextTab.key, "all"))}><XCircle size={14} />关闭全部标签页</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>, portalRoot,
  );
}
