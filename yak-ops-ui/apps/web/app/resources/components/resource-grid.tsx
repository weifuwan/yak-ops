import type { MouseEvent } from "react";
import type { ResourceItemRecord } from "@/service/resources";

import { ResourceActions } from "./resource-actions";
import { ResourceFileIcon } from "./resource-file-icon";
import { formatSize, getDateParts, getName, isDirectoryItem } from "../utils";

interface ResourceGridProps {
  items: ResourceItemRecord[];
  selectedKeys: Set<string>;
  onToggleSelection: (item: ResourceItemRecord) => void;
  onSelect: (item: ResourceItemRecord) => void;
  onContextMenu: (item: ResourceItemRecord, event: MouseEvent<HTMLElement>) => void;
  onOpen: (item: ResourceItemRecord) => void;
  onDownload: (item: ResourceItemRecord) => void;
  onRename: (item: ResourceItemRecord) => void;
  onDelete: (item: ResourceItemRecord) => void;
}

export function ResourceGrid({
  items,
  selectedKeys,
  onToggleSelection,
  onSelect,
  onContextMenu,
  onOpen,
  onDownload,
  onRename,
  onDelete,
}: ResourceGridProps) {
  return (
    <div className="min-h-0 flex-1 overflow-auto p-4">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
        {items.map((item) => {
          const directory = isDirectoryItem(item);
          const name = getName(item);
          const date = getDateParts(item.updateTime);
          const selected = selectedKeys.has(item.fullName);

          return (
            <article
              key={item.fullName}
              className={[
                "group relative flex min-w-0 items-center gap-3 rounded-lg border bg-white p-3 transition-colors",
                selected
                  ? "border-[var(--yak-color-primary)] bg-[#fffafa]"
                  : "border-[#edf0f3] hover:border-[#dfe4ea] hover:bg-[#fcfcfd]",
              ].join(" ")}
              onClick={(event) => {
                const target = event.target as HTMLElement;
                if (target.closest("button, input")) return;
                onSelect(item);
              }}
              onDoubleClick={(event) => {
                const target = event.target as HTMLElement;
                if (target.closest("[data-resource-action], input")) return;
                if (directory) onOpen(item);
                else onDownload(item);
              }}
              onContextMenu={(event) => onContextMenu(item, event)}
            >
              <input
                type="checkbox"
                aria-label={"选择" + name}
                checked={selected}
                onClick={(event) => event.stopPropagation()}
                onDoubleClick={(event) => event.stopPropagation()}
                onChange={() => onToggleSelection(item)}
                className="size-4 shrink-0 cursor-pointer accent-[var(--yak-color-primary)]"
              />
              <ResourceFileIcon item={item} large />
              <button
                type="button"
                data-resource-name
                onClick={() => onSelect(item)}
                onDoubleClick={(event) => {
                  event.stopPropagation();
                  if (directory) onOpen(item);
                  else onDownload(item);
                }}
                title={name}
                className="min-w-0 flex-1 cursor-pointer text-left"
              >
                <span className="block truncate text-[13px] font-medium text-[#303846]">
                  {name}
                </span>
                <span className="mt-1 block text-[11px] text-[#9aa2ad]">
                  {directory ? "文件夹" : formatSize(item.size)}
                </span>
              </button>
              <div className="flex shrink-0 flex-col items-end gap-1">
                <span className="text-[11px] text-[#687386]">{date.date}</span>
                <span className="text-[10px] text-[#a0a7b2]">{date.time}</span>
              </div>
              <ResourceActions
                item={item}
                onOpen={onOpen}
                onDownload={onDownload}
                onRename={onRename}
                onDelete={onDelete}
              />
            </article>
          );
        })}
      </div>
    </div>
  );
}
