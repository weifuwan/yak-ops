import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import {
  Copy,
  Download,
  FilePenLine,
  FolderOpen,
  Pencil,
  Replace,
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
import { canEditResource, isDirectoryItem } from "../utils";

interface ResourceContextMenuProps {
  item: ResourceItemRecord | null;
  position: { x: number; y: number } | null;
  onClose: () => void;
  onOpen: (item: ResourceItemRecord) => void;
  onEdit: (item: ResourceItemRecord) => void;
  onDownload: (item: ResourceItemRecord) => void;
  onReplace: (item: ResourceItemRecord) => void;
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
  onEdit,
  onDownload,
  onReplace,
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
  const editable = canEditResource(item);

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
        {directory ? (
          <DropdownMenuItem onClick={() => onOpen(item)}>
            <FolderOpen size={14} />
            打开文件夹
          </DropdownMenuItem>
        ) : (
          <>
            {editable ? (
              <DropdownMenuItem onClick={() => onEdit(item)}>
                <FilePenLine size={14} />
                编辑文件
              </DropdownMenuItem>
            ) : null}
            <DropdownMenuItem onClick={() => onDownload(item)}>
              <Download size={14} />
              下载文件
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => onReplace(item)}>
              <Replace size={14} />
              替换文件
            </DropdownMenuItem>
          </>
        )}
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
