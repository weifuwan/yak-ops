import {
  Copy,
  Download,
  FolderOpen,
  Pencil,
  Trash2,
  X,
} from "lucide-react";
import { Button } from "@yak-ops/yak-ui";

import type { ResourceItemRecord } from "@/service/resources";
import { getName, isDirectoryItem } from "../utils";

interface ResourceSelectionToolbarProps {
  items: ResourceItemRecord[];
  onOpen: (item: ResourceItemRecord) => void;
  onDownload: (item: ResourceItemRecord) => void;
  onRename: (item: ResourceItemRecord) => void;
  onCopyPath: (item: ResourceItemRecord) => void;
  onDelete: () => void;
  onClear: () => void;
}

export function ResourceSelectionToolbar({
  items,
  onOpen,
  onDownload,
  onRename,
  onCopyPath,
  onDelete,
  onClear,
}: ResourceSelectionToolbarProps) {
  if (items.length === 0) return null;

  const single = items.length === 1 ? items[0] : null;

  return (
    <div className="mb-3 flex min-h-10 flex-wrap items-center justify-between gap-2 rounded-lg border border-[#f1d9de] bg-[#fffafb] px-3 py-1.5">
      <div className="flex items-center gap-2 text-xs">
        <span className="flex size-5 items-center justify-center rounded-full bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
          {items.length}
        </span>
        <span className="font-medium text-[#394150]">已选择 {items.length} 项</span>
        {single ? (
          <span className="hidden max-w-48 truncate text-[#929aa6] sm:inline">
            {getName(single)}
          </span>
        ) : null}
      </div>

      <div className="flex flex-wrap items-center gap-1">
        {single ? (
          <>
            {isDirectoryItem(single) ? (
              <Button
                size="sm"
                variant="ghost"
                className="h-7 px-2 text-xs"
                onClick={() => onOpen(single)}
              >
                <FolderOpen size={13} />
                打开
              </Button>
            ) : (
              <Button
                size="sm"
                variant="ghost"
                className="h-7 px-2 text-xs"
                onClick={() => onDownload(single)}
              >
                <Download size={13} />
                下载
              </Button>
            )}
            <Button
              size="sm"
              variant="ghost"
              className="h-7 px-2 text-xs"
              onClick={() => onRename(single)}
            >
              <Pencil size={13} />
              重命名
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="h-7 px-2 text-xs"
              onClick={() => onCopyPath(single)}
            >
              <Copy size={13} />
              复制路径
            </Button>
          </>
        ) : null}
        <Button
          size="sm"
          variant="ghost"
          className="h-7 px-2 text-xs text-[var(--yak-color-danger)] hover:text-[var(--yak-color-danger)]"
          onClick={onDelete}
        >
          <Trash2 size={13} />
          删除
        </Button>
        <Button
          size="sm"
          variant="ghost"
          isIconOnly
          aria-label="取消选择"
          title="取消选择"
          className="size-7"
          onClick={onClear}
        >
          <X size={13} />
        </Button>
      </div>
    </div>
  );
}
