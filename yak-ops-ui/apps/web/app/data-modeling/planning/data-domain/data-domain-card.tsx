import { Package, SquarePen, Trash2 } from "lucide-react";
import { Button, Card } from "@yak-ops/yak-ui";

import { isSystemRecord, type DataDomainRecord } from "./types";

interface DataDomainCardProps {
  record: DataDomainRecord;
  opening?: boolean;
  onOpen: (record: DataDomainRecord) => void;
  onEdit: (record: DataDomainRecord) => void;
  onDelete: (record: DataDomainRecord) => void;
}

export function DataDomainCard({ record, opening = false, onOpen, onEdit, onDelete }: DataDomainCardProps) {
  const system = isSystemRecord(record);
  return (
    <Card
      className="flex h-full flex-col transition-shadow duration-150 hover:shadow-[0px_5px_5px_0px_rgba(0,0,0,0.12)]"
      style={{ minHeight: 182, boxSizing: "border-box", border: "1px solid rgba(0,0,0,0.1)", borderRadius: 6, backgroundColor: "#fff", boxShadow: "0 2px 8px #0000000d" }}
    >
      <button
        type="button"
        aria-label={"查看 " + (record.cnName || "数据域") + " 详情"}
        onClick={() => onOpen(record)}
        disabled={opening}
        className="flex w-full min-w-0 flex-1 cursor-pointer items-start border-0 bg-transparent text-left outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--yak-color-primary)] disabled:cursor-wait"
        style={{ padding: "14px 14px 0", font: "inherit" }}
      >
        <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded" style={{ backgroundColor: "rgba(246,246,246,1)" }}>
          <Package size={18} strokeWidth={1.7} color="rgba(128,128,128,1)" />
        </span>
        <span className="min-w-0 flex-1 pl-3">
          <span className="flex min-w-0 items-start justify-between gap-1">
            <span className="block min-w-0 truncate text-[15px] font-bold leading-6 text-[#16181d]">{record.cnName || "-"}</span>
            {system ? <span className="shrink-0 rounded border border-[#91caff] bg-[#e6f4ff] px-1.5 py-0.5 text-[10px] leading-4 text-[#1677ff]">系统默认</span> : null}
          </span>
          <span className="block truncate text-sm font-semibold leading-6 text-[#16181d]">{record.enShortName || "-"}</span>
          <span className="line-clamp-2 block min-h-9 overflow-hidden text-xs leading-[18px] text-[rgba(78,83,90,1)]" title={record.remark || ""}>{record.remark || "暂无备注"}</span>
          <span className="mt-1 block truncate text-xs leading-[18px] text-[#9ca1ba]">最后更新：{record.updateTime || "-"}</span>
          <span className="block truncate text-xs leading-[18px] text-[#9ca1ba]">负责人：{record.director || "-"}</span>
        </span>
      </button>
      <div className="flex h-[44px] shrink-0 items-center border-t border-[#f4f6f9] px-3" style={{ boxSizing: "border-box" }}>
        <div className="flex flex-1 items-center justify-center">
          <Button size="sm" variant="ghost" isDisabled={opening} className="inline-flex h-6 gap-1.5 rounded-none px-1 text-[var(--yak-color-primary)]" onClick={() => onEdit(record)}>
            <SquarePen size={14} strokeWidth={1.8} /><span className="text-xs">修改</span>
          </Button>
        </div>
        {!system ? (
          <div className="flex flex-1 items-center justify-center">
            <Button size="sm" variant="ghost" className="inline-flex h-6 gap-1.5 rounded-none px-1 text-[var(--yak-color-primary)]" onClick={() => onDelete(record)}>
              <Trash2 size={14} strokeWidth={1.8} /><span className="text-xs">删除</span>
            </Button>
          </div>
        ) : null}
      </div>
    </Card>
  );
}

export default DataDomainCard;
