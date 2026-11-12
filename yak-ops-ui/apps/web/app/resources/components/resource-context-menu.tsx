import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import {
  Copy,
  Download,
  FolderOpen,
  Pencil,
  Trash2,
  Info,
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

interface ResourceContextMenuProps {
  item: ResourceItemRecord | null;
  position: { x: number; y: number } | null;
  onClose: () => void;
  onOpen: (item: ResourceItemRecord) => void;
  onDownload: (item: ResourceItemRecord) => void;
  onRename: (item: ResourceItemRecord) => void;
  onDelete: (item: ResourceItemRecord) => void;
  onCopyPath: (item: ResourceItemRecord) => void;
  onShowDetails: () => void;
}

export function ResourceContextMenu({
  item,
  position,
  onClose,
  onOpen,
  onDownload,
  onRename,
  onDelete,
  onCopyPath,
  onShowDetails,
}: ResourceContextMenuProps) {
  const [portalRoot, setPortalRoot] = useState<HTMLElement | null>(null);

  useEffect(() => {
    setPortalRoot(document.body);
  }, []);

  if (!item || !position || !portalRoot) return null;

  const directory = isDirectoryItem(item);

  return createPortal(
    <DropdownMenu
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DropdownMenuTrigger
        aria-hidden="true"
        tabIndex={-1}
        className="pointer-events-none fixed size-px opacity-0"
        style={{ left: position.x, top: position.y }}
      >
        <span />
      </DropdownMenuTrigger>
      <DropdownMenuContent
        side="bottom"
        align="start"
        sideOffset={0}
        className="w-44 rounded-md p-1"
      >
        <DropdownMenuItem onClick={() => directory ? onOpen(item) : onDownload(item)}>
          {directory ? <FolderOpen size={14} /> : <Download size={14} />}
          {directory ? "打开文件夹" : "下载文件"}
        </DropdownMenuItem>
        <DropdownMenuItem onClick={onShowDetails}>
          <Info size={14} />
          查看详情
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => onCopyPath(item)}>
          <Copy size={14} />
          复制完整路径
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => onRename(item)}>
          <Pencil size={14} />
          重命名
        </DropdownMenuItem>
        <DropdownMenuItem tone="danger" onClick={() => onDelete(item)}>
          <Trash2 size={14} />
          删除
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>,
    portalRoot,
  );
}
