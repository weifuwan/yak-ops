import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@yak-ops/yak-ui";
import { ArrowLeftToLine, ArrowRightToLine, Ellipsis, X, XCircle } from "lucide-react";

import { IndicatorIcon, getIndicatorIconName } from "../../../icons";
import type { IndicatorTab } from "../../types";

type CloseAction = "current" | "other" | "left" | "right" | "all";
type IndicatorTabBarProps = {
  tabs: IndicatorTab[];
  activeTabKey: string;
  activeTab: IndicatorTab | null;
  onSelect: (key: string) => void;
  onClose: (keys: string[]) => void;
  onContextMenu: (tabKey: string, x: number, y: number) => void;
  getTabKeysToClose: (anchorKey: string, action: CloseAction) => string[];
};

export function IndicatorTabBar({ tabs, activeTabKey, activeTab, onSelect, onClose, onContextMenu, getTabKeysToClose }: IndicatorTabBarProps) {
  return tabs.length ? (
    <div className="flex h-[35px] min-w-0 shrink-0 items-center gap-1 overflow-hidden border-b border-[#e2e2e2] bg-[#f2f2f2] px-2">
      <div role="tablist" aria-label="指标工作区选项卡" className="flex h-full min-w-0 flex-1 items-center gap-1 overflow-x-auto overflow-y-hidden [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
        {tabs.map((tab) => {
          const active = tab.key === activeTabKey;
          return <div key={tab.key} onContextMenu={(event) => { event.preventDefault(); event.stopPropagation(); onContextMenu(tab.key, event.clientX, event.clientY); }} className={[
            "group relative flex h-[27px] w-fit min-w-[120px] max-w-[240px] shrink-0 items-center rounded-[3px]",
            active ? "bg-[#cedaff] text-[#26282c]" : "bg-transparent text-[#616161] hover:bg-[#e8e8e8]",
          ].join(" ")}>
            <button type="button" role="tab" aria-selected={active} tabIndex={active ? 0 : -1} title={tab.dirty ? tab.label + "（有未保存修改）" : tab.label} onClick={() => onSelect(tab.key)} className="flex h-full min-w-0 flex-1 cursor-pointer items-center gap-2 pl-2 pr-0 text-left text-[13px] leading-none outline-none">
              <IndicatorIcon name={getIndicatorIconName(tab.kind)} size={16} />
              <span className="min-w-0 flex-1 truncate whitespace-nowrap">{tab.label}</span>
              {tab.dirty ? <span aria-label="有未保存修改" title="有未保存修改" className="mr-1.5 size-1.5 shrink-0 rounded-full bg-[#e6a23c]" /> : null}
            </button>
            <button type="button" aria-label={"关闭 " + tab.label} title={"关闭 " + tab.label} onClick={() => onClose([tab.key])} className={[
              "mr-1 flex size-5 shrink-0 cursor-pointer items-center justify-center rounded-sm text-[#343434] transition-opacity hover:bg-black/5 focus-visible:opacity-100 focus-visible:outline-none",
              active ? "opacity-100" : "opacity-0 group-hover:opacity-100",
            ].join(" ")}><X size={15} strokeWidth={1.8} /></button>
          </div>;
        })}
      </div>
      <DropdownMenu>
        <DropdownMenuTrigger aria-label="标签页更多操作" title="标签页更多操作" disabled={!activeTab} className="flex h-[27px] w-8 shrink-0 cursor-pointer items-center justify-center rounded-[3px] border-0 bg-transparent p-0 text-[#616161] outline-none transition-colors hover:bg-[#e8e8e8] hover:text-[#26282c] focus-visible:ring-1 focus-visible:ring-[#9bb6ff] disabled:cursor-not-allowed disabled:opacity-40"><Ellipsis size={18} strokeWidth={1.8} /></DropdownMenuTrigger>
        <DropdownMenuContent side="bottom" align="end" sideOffset={4} className="w-48">
          <DropdownMenuItem disabled={!activeTab} onClick={() => onClose(getTabKeysToClose(activeTabKey, "current"))}><X size={14} />关闭当前标签页</DropdownMenuItem>
          <DropdownMenuItem disabled={!activeTab || tabs.length < 2} onClick={() => onClose(getTabKeysToClose(activeTabKey, "other"))}><XCircle size={14} />关闭其他标签页</DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem disabled={!activeTab || getTabKeysToClose(activeTabKey, "left").length === 0} onClick={() => onClose(getTabKeysToClose(activeTabKey, "left"))}><ArrowLeftToLine size={14} />关闭左侧标签页</DropdownMenuItem>
          <DropdownMenuItem disabled={!activeTab || getTabKeysToClose(activeTabKey, "right").length === 0} onClick={() => onClose(getTabKeysToClose(activeTabKey, "right"))}><ArrowRightToLine size={14} />关闭右侧标签页</DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem tone="danger" disabled={tabs.length === 0} onClick={() => onClose(getTabKeysToClose(activeTabKey, "all"))}><XCircle size={14} />关闭全部标签页</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  ) : null;
}
