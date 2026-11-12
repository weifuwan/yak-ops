import { Copy, PanelRight, X } from "lucide-react";
import { Button } from "@yak-ops/yak-ui";

import type { ResourceItemRecord } from "@/service/resources";

import { ResourceFileIcon } from "./resource-file-icon";
import { formatSize, getDateParts, getExtension, getName, isDirectoryItem } from "../utils";

interface ResourceDetailsPanelProps {
  items: ResourceItemRecord[];
  onClose: () => void;
  onCopyPath: (item: ResourceItemRecord) => void;
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid grid-cols-[68px_minmax(0,1fr)] gap-2 text-xs">
      <span className="text-[#929aa6]">{label}</span>
      <span className="min-w-0 break-words text-[#394150]">{value || "—"}</span>
    </div>
  );
}

export function ResourceDetailsPanel({
  items,
  onClose,
  onCopyPath,
}: ResourceDetailsPanelProps) {
  const item = items.length === 1 ? items[0] : null;

  return (
    <aside className="flex w-[280px] max-w-[36%] shrink-0 flex-col overflow-hidden rounded-lg border border-[var(--yak-components-panel-border)] bg-white">
      <header className="flex h-11 shrink-0 items-center justify-between border-b border-[#edf0f3] px-3">
        <div className="flex items-center gap-2 text-[13px] font-medium text-[#394150]">
          <PanelRight size={14} className="text-[#8b94a2]" />
          资源详情
        </div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          isIconOnly
          aria-label="关闭详情面板"
          title="关闭"
          className="size-7"
          onClick={onClose}
        >
          <X size={14} />
        </Button>
      </header>

      {item ? (
        <div className="min-h-0 flex-1 overflow-auto p-4">
          <div className="flex flex-col items-center gap-2 border-b border-[#edf0f3] pb-4">
            <ResourceFileIcon item={item} large />
            <div className="max-w-full truncate text-center text-sm font-medium text-[#303846]" title={getName(item)}>
              {getName(item)}
            </div>
            <span className="text-[11px] text-[#929aa6]">
              {isDirectoryItem(item)
                ? "文件夹"
                : getExtension(getName(item)).toUpperCase() || "文件"}
            </span>
          </div>

          <div className="space-y-3 py-4">
            <div className="text-xs font-semibold text-[#394150]">基本信息</div>
            <DetailRow label="类型" value={isDirectoryItem(item) ? "文件夹" : getExtension(getName(item)).toUpperCase() || "文件"} />
            <DetailRow label="大小" value={isDirectoryItem(item) ? "—" : formatSize(item.size)} />
            <DetailRow label="创建时间" value={getDateParts(item.createTime).date + (getDateParts(item.createTime).time ? " " + getDateParts(item.createTime).time : "")} />
            <DetailRow label="修改时间" value={getDateParts(item.updateTime).date + (getDateParts(item.updateTime).time ? " " + getDateParts(item.updateTime).time : "")} />
          </div>

          <div className="border-t border-[#edf0f3] pt-4">
            <div className="mb-2 text-xs font-semibold text-[#394150]">存储位置</div>
            <div className="break-all rounded-md bg-[#f7f8fa] p-2 text-[11px] leading-5 text-[#687386]">
              {item.fullName}
            </div>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              className="mt-2 h-7 px-2 text-xs"
              onClick={() => onCopyPath(item)}
            >
              <Copy size={13} />
              复制完整路径
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-2 px-5 text-center">
          <PanelRight size={25} strokeWidth={1.4} className="text-[#c4cbd4]" />
          <span className="text-xs font-medium text-[#687386]">
            {items.length > 1 ? "已选择 " + items.length + " 项资源" : "选择资源查看详情"}
          </span>
          <span className="text-[11px] leading-5 text-[#a0a7b2]">
            {items.length > 1
              ? "同时选择多个资源时，详细属性暂不展示"
              : "选中文件或文件夹后，这里会显示类型、大小和存储位置"}
          </span>
        </div>
      )}
    </aside>
  );
}
