import { Clock3, Database, Pencil, Trash2, UserRound } from "lucide-react";
import { Button, Card, CardContent } from "@yak-ops/yak-ui";

import {
  getDwModelTypeLabel,
  getDwTypeLabel,
  type DwLayerRecord,
} from "./constants";

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
    <Card className="h-full min-h-[194px] transition-[border-color] duration-150 hover:border-[var(--yak-color-primary)]">
      <CardContent className="flex h-full flex-col p-0">
        <button
          type="button"
          onClick={() => onOpen(record)}
          className="flex min-h-[148px] flex-1 cursor-pointer items-start gap-3 p-4 text-left outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--yak-color-primary)]"
        >
          <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-[var(--yak-color-hover)] text-[var(--yak-color-primary)]">
            <Database size={19} strokeWidth={1.8} />
          </span>

          <span className="min-w-0 flex-1">
            <span className="flex min-w-0 items-center gap-2">
              <span className="truncate text-sm font-semibold text-[var(--yak-components-panel-text)]">
                {record.cnName || "未命名分层"}
              </span>
              <span className="shrink-0 rounded bg-[var(--yak-color-hover)] px-1.5 py-0.5 text-[10px] text-[var(--yak-components-muted-text)]">
                {getDwTypeLabel(record.dwType, record.dwTypeName)}
              </span>
            </span>

            <span className="mt-1 block truncate text-xs text-[var(--yak-components-muted-text)]">
              {record.enShortName || "-"}
              {record.enName ? " · " + record.enName : ""}
            </span>

            <span className="mt-3 line-clamp-2 block min-h-9 text-xs leading-[18px] text-[var(--yak-components-panel-text)]/75">
              {record.remark || "暂无备注"}
            </span>

            <span className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-[var(--yak-components-muted-text)]">
              <span className="inline-flex min-w-0 items-center gap-1">
                <Clock3 size={12} className="shrink-0" />
                <span className="truncate">{record.updateTime || "暂无更新"}</span>
              </span>
              <span className="inline-flex min-w-0 items-center gap-1">
                <UserRound size={12} className="shrink-0" />
                <span className="truncate">{record.director || "未指定负责人"}</span>
              </span>
            </span>
          </span>

          <span className="sr-only">
            {getDwModelTypeLabel(record.dwModelType)}
          </span>
        </button>

        <div className="flex h-10 shrink-0 items-center justify-end gap-1 border-t border-[var(--yak-components-panel-border)] px-3">
          <span className="mr-auto truncate text-[11px] text-[var(--yak-components-muted-text)]">
            {getDwModelTypeLabel(record.dwModelType)}
          </span>
          <Button
            size="sm"
            variant="ghost"
            isDisabled={opening}
            className="h-7 px-2 text-xs font-normal text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-primary)]"
            onClick={() => onEdit(record)}
          >
            <Pencil size={12} />
            修改
          </Button>
          <Button
            size="sm"
            variant="ghost"
            className="h-7 px-2 text-xs font-normal text-[var(--yak-components-muted-text)] hover:text-[#d92d20]"
            onClick={() => onDelete(record)}
          >
            <Trash2 size={12} />
            删除
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

export default DwLayerCard;
