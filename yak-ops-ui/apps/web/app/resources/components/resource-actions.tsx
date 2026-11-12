import {
  Download,
  FolderOpen,
  MoreHorizontal,
  Pencil,
  Trash2,
} from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";

import type { ResourceItemRecord } from "@/service/resources";

import { getName, isDirectoryItem } from "../utils";

interface ResourceActionsProps {
  item: ResourceItemRecord;
  onOpen: (item: ResourceItemRecord) => void;
  onDownload: (item: ResourceItemRecord) => void;
  onRename: (item: ResourceItemRecord) => void;
  onDelete: (item: ResourceItemRecord) => void;
}

export function ResourceActions({
  item,
  onOpen,
  onDownload,
  onRename,
  onDelete,
}: ResourceActionsProps) {
  const directory = isDirectoryItem(item);
  const name = getName(item);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={"更多操作：" + name}
        title="更多操作"
        className="inline-flex size-8 cursor-pointer items-center justify-center rounded-md border-0 bg-transparent text-[#89919d] outline-none transition-colors hover:bg-[#f1f3f6] hover:text-[#303846] focus-visible:ring-2 focus-visible:ring-[var(--yak-color-primary)]"
      >
        <MoreHorizontal size={17} />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" side="bottom" className="w-36">
        {directory ? (
          <DropdownMenuItem onClick={() => onOpen(item)}>
            <FolderOpen size={14} />
            打开文件夹
          </DropdownMenuItem>
        ) : (
          <DropdownMenuItem onClick={() => onDownload(item)}>
            <Download size={14} />
            下载
          </DropdownMenuItem>
        )}
        <DropdownMenuItem onClick={() => onRename(item)}>
          <Pencil size={14} />
          重命名
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem tone="danger" onClick={() => onDelete(item)}>
          <Trash2 size={14} />
          删除
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
