import { Trash2 } from "lucide-react";
import type { IndicatorTreeContextMenuState } from "../../types";

type Props = { contextMenu: IndicatorTreeContextMenuState; onDelete: () => void; onClose: () => void };

export function IndicatorTreeContextMenu({ contextMenu, onDelete, onClose }: Props) {
  if (!contextMenu) return null;
  return <>
    <button type="button" aria-label="关闭右键菜单" className="fixed inset-0 z-40 cursor-default" onClick={onClose} />
    <div className="fixed z-50 min-w-32 rounded-lg border border-[var(--yak-components-panel-border)] bg-white p-1 shadow-[var(--yak-components-panel-shadow)]" style={{ left: contextMenu.x, top: contextMenu.y }}>
      <button type="button" className="flex w-full items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[var(--yak-components-danger-text)] hover:bg-[var(--yak-color-hover)]" onClick={onDelete}><Trash2 size={13} />删除</button>
    </div>
  </>;
}
