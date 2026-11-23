import { List, Table2, X } from "lucide-react";

import { ModelTypeIcon, isModelTypeIconType } from "../../../../components/model-type-icon";
import type { DimensionTab } from "../types";

type Props = {
  tabs: DimensionTab[];
  activeTabKey: string;
  onSelect: (key: string) => void;
  onClose: (key: string) => void;
};

export function DimensionTabBar({ tabs, activeTabKey, onSelect, onClose }: Props) {
  if (!tabs.length) return null;

  return (
    <div className="flex h-[35px] min-w-0 shrink-0 items-center gap-1 overflow-hidden border-b border-[#e2e2e2] bg-[#f2f2f2] px-2">
      <div role="tablist" aria-label="维度建模工作区选项卡" className="flex h-full min-w-0 flex-1 items-center gap-1 overflow-x-auto overflow-y-hidden [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
        {tabs.map((tab) => {
          const active = tab.key === activeTabKey;
          const modelType = tab.createType || tab.record?.tableType;
          const hasModelTypeIcon = isModelTypeIconType(modelType);
          return (
            <div
              key={tab.key}
              className={[
                "group relative flex h-[27px] w-fit min-w-[120px] max-w-[240px] shrink-0 items-center rounded-[3px]",
                active ? "bg-[#cedaff] text-[#26282c]" : "bg-transparent text-[#616161] hover:bg-[#e8e8e8]",
              ].join(" ")}
            >
              <button
                type="button"
                role="tab"
                aria-selected={active}
                tabIndex={active ? 0 : -1}
                title={tab.dirty ? tab.label + "（有未保存修改）" : tab.label}
                onClick={() => onSelect(tab.key)}
                className="flex h-full min-w-0 flex-1 cursor-pointer items-center gap-2 pl-2 pr-0 text-left text-[13px] leading-none outline-none"
              >
                {tab.mode === "list" ? (
                  <List size={15} />
                ) : hasModelTypeIcon ? (
                  <ModelTypeIcon type={modelType} size={15} />
                ) : (
                  <Table2 size={15} />
                )}
                <span className="min-w-0 flex-1 truncate whitespace-nowrap">{tab.label}</span>
                {tab.dirty ? <span aria-label="有未保存修改" title="有未保存修改" className="mr-1.5 size-1.5 shrink-0 rounded-full bg-[#e6a23c]" /> : null}
              </button>
              <button
                type="button"
                aria-label={"关闭 " + tab.label}
                title={"关闭 " + tab.label}
                onClick={() => onClose(tab.key)}
                className={[
                  "mr-1 flex size-5 shrink-0 cursor-pointer items-center justify-center rounded-sm text-[#343434] transition-opacity hover:bg-black/5 focus-visible:opacity-100 focus-visible:outline-none",
                  active ? "opacity-100" : "opacity-0 group-hover:opacity-100",
                ].join(" ")}
              >
                <X size={14} strokeWidth={1.8} />
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default DimensionTabBar;
