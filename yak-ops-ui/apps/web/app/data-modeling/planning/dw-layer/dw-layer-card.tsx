import { Box, Pencil, Trash2 } from "lucide-react";
import { Button, Card, CardContent } from "@yak-ops/yak-ui";

import type { DwLayerRecord } from "./constants";

interface DwLayerCardProps {
  record: DwLayerRecord;
  opening?: boolean;
  onOpen: (record: DwLayerRecord) => void;
  onEdit: (record: DwLayerRecord) => void;
  onDelete: (record: DwLayerRecord) => void;
}

export function DwLayerCard({
  record,
  opening = false,
  onOpen,
  onEdit,
  onDelete,
}: DwLayerCardProps) {
  return (
    <Card className="h-full min-h-[200px] rounded-[6px] border-[rgba(0,0,0,0.1)] bg-white shadow-[0_2px_8px_rgba(0,0,0,0.05)] transition-shadow duration-150 hover:shadow-[0_5px_12px_rgba(0,0,0,0.1)]">
      <CardContent className="flex h-full min-h-[198px] flex-col p-0">
        <button
          type="button"
          aria-label={"查看 " + (record.cnName || "数仓分层") + " 详情"}
          onClick={() => onOpen(record)}
          className="flex min-h-[149px] flex-1 cursor-pointer items-start p-4 pb-2 text-left outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--yak-color-primary)]"
        >
          <span className="flex size-10 shrink-0 items-center justify-center rounded-[4px] bg-[#f6f6f6] text-[#999]">
            <Box size={19} strokeWidth={1.7} />
          </span>

          <span className="min-w-0 flex-1 pl-4">
            <span className="block truncate text-[15px] font-bold leading-6 text-[#26282c]">
              {record.cnName || "未命名分层"}
            </span>
            <span className="block truncate text-[14px] font-bold leading-5 text-[#26282c]">
              {record.enShortName || "-"}
            </span>

            <span className="mt-0.5 line-clamp-2 block min-h-9 text-xs leading-[18px] text-[#4e535a]">
              {record.remark || ""}
            </span>

            <span className="mt-1 block truncate text-xs leading-[18px] text-[#9ca1ba]">
              最后更新：{record.updateTime || "暂无记录"}
            </span>
            <span className="block truncate text-xs leading-[18px] text-[#9ca1ba]">
              负责人：{record.director || "-"}
            </span>
          </span>
        </button>

        <div className="flex h-[49px] shrink-0 items-center border-t border-[#f4f6f9] px-4">
          <Button
            size="sm"
            variant="ghost"
            isDisabled={opening}
            className="h-8 min-w-0 flex-1 justify-center gap-1.5 rounded-sm px-2 text-xs font-medium text-[var(--yak-color-primary)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]"
            onClick={() => onEdit(record)}
          >
            <Pencil size={13} strokeWidth={1.8} />
            修改
          </Button>
          <Button
            size="sm"
            variant="ghost"
            className="h-8 min-w-0 flex-1 justify-center gap-1.5 rounded-sm px-2 text-xs font-medium text-[var(--yak-color-primary)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]"
            onClick={() => onDelete(record)}
          >
            <Trash2 size={13} strokeWidth={1.8} />
            删除
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

export default DwLayerCard;
