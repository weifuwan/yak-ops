import { SquarePen, Trash2 } from "lucide-react";
import { Button, Card } from "@yak-ops/yak-ui";

import type { DwLayerRecord } from "./constants";

interface DwLayerCardProps {
  record: DwLayerRecord;
  opening?: boolean;
  onOpen: (record: DwLayerRecord) => void;
  onEdit: (record: DwLayerRecord) => void;
  onDelete: (record: DwLayerRecord) => void;
}

const CUBE_ICON_PATH =
  "M918.080635 733.426746l-373.594184 203.040317V423.903062l373.594184-164.237056v473.76074z m-817.575678 0s-2.707204-2.707204-2.707204-5.414408v-469.248734l378.106191 164.237057v513.466402L100.504957 733.426746zM499.36638 71.966512c7.219211-2.707204 15.340824-2.707204 22.560036 0l345.61974 135.360212-355.546156 156.115444L153.74664 207.326724l345.61974-135.360212zM940.64067 163.109055L544.486451 6.09121c-22.560035-8.121613-47.827275-8.121613-70.38731 0L77.944922 163.109055c-27.974444 10.828817-45.120071 38.803261-45.120071 70.38731v495.418374c0 26.169641 12.63362 52.339282 35.193655 64.972901h2.707205l406.080634 219.283543c9.926416 5.414408 22.560035 10.828817 35.193655 10.828817s25.267239-2.707204 35.193655-10.828817l406.080634-219.283543h2.707205c22.560035-12.63362 35.193655-38.803261 35.193655-64.972901V233.496365c-5.414408-31.584049-22.560071-60.460894-50.534479-70.38731z";

export function DwLayerCard({
  record,
  opening = false,
  onOpen,
  onEdit,
  onDelete,
}: DwLayerCardProps) {
  return (
    <Card
      className="flex h-full flex-col shadow-[0_2px_8px_#0000000d] transition-shadow duration-150 hover:shadow-[0px_5px_5px_0px_rgba(0,0,0,0.2)]"
      style={{
        minHeight: 200,
        boxSizing: "border-box",
        border: "1px solid rgba(0, 0, 0, 0.1)",
        borderRadius: 6,
        backgroundColor: "#fff",
      }}
    >
      <button
        type="button"
        aria-label={"查看 " + (record.cnName || "数仓分层") + " 详情"}
        onClick={() => onOpen(record)}
        className="flex w-full flex-1 cursor-pointer items-start border-0 bg-transparent text-left outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--yak-color-primary)]"
        style={{
          minWidth: 0,
          padding: "16px 16px 0",
          font: "inherit",
        }}
      >
        <span
          aria-hidden="true"
          className="flex shrink-0 items-center justify-center"
          style={{
            boxSizing: "border-box",
            width: 40,
            height: 40,
            padding: "10px 12px",
            borderRadius: 4,
            backgroundColor: "rgba(246, 246, 246, 1)",
          }}
        >
          <svg viewBox="0 0 1024 1024" width="18" height="19" xmlns="http://www.w3.org/2000/svg" style={{ display: "block", flex: "none" }}>
            <path d={CUBE_ICON_PATH} fill="rgba(128,128,128,1)" />
          </svg>
        </span>

        <span
          className="min-w-0 flex-1"
          style={{ position: "relative", boxSizing: "border-box", paddingLeft: 16 }}
        >
          <span
            className="block truncate"
            style={{ margin: 0, overflow: "hidden", color: "#000", fontWeight: 700, fontSize: 16, lineHeight: "24px", whiteSpace: "nowrap", textOverflow: "ellipsis" }}
          >
            {record.cnName}
          </span>
          <span
            className="block truncate"
            style={{ margin: 0, overflow: "hidden", color: "#000", fontWeight: 700, fontSize: 14, lineHeight: "24px", whiteSpace: "nowrap", textOverflow: "ellipsis" }}
          >
            {record.enShortName}
          </span>
          <span
            className="line-clamp-2 block"
            style={{ display: "-webkit-box", margin: "0 0 4px", overflow: "hidden", color: "rgba(78, 83, 90, 1)", fontSize: 12, lineHeight: "18px", WebkitBoxOrient: "vertical", WebkitLineClamp: 2 }}
          >
            {record.remark}
          </span>
          <span
            className="block truncate"
            style={{ margin: "4px 0", overflowX: "hidden", color: "#9ca1ba", fontSize: 12, lineHeight: "18px", whiteSpace: "nowrap", textOverflow: "ellipsis" }}
          >
            最后更新：{record.updateTime}
          </span>
          <span
            className="block truncate"
            style={{ margin: "4px 0", overflowX: "hidden", color: "#9ca1ba", fontSize: 12, lineHeight: "18px", whiteSpace: "nowrap", textOverflow: "ellipsis" }}
          >
            负责人：{record.director}
          </span>
        </span>
      </button>

      <div
        className="flex shrink-0 flex-row"
        style={{ width: "100%", height: 49, boxSizing: "border-box", padding: "0 16px", borderTop: "1px solid #f4f6f9" }}
      >
        <div className="flex flex-1 items-center justify-center" style={{ lineHeight: "49px", textAlign: "center" }}>
          <Button
            size="sm"
            variant="ghost"
            isDisabled={opening}
            className="inline-flex"
            style={{ height: 24, padding: "0 2px", borderRadius: 0, color: "var(--yak-color-primary)", fontSize: 14, fontWeight: 500, gap: 8 }}
            onClick={() => onEdit(record)}
          >
            <SquarePen size={14} strokeWidth={1.8} />
            <span style={{ fontSize: 12 }}>修改</span>
          </Button>
        </div>
        <div className="flex flex-1 items-center justify-center" style={{ lineHeight: "49px", textAlign: "center" }}>
          <Button
            size="sm"
            variant="ghost"
            className="inline-flex"
            style={{ height: 24, padding: "0 2px", borderRadius: 0, color: "var(--yak-color-primary)", fontSize: 14, fontWeight: 500, gap: 8 }}
            onClick={() => onDelete(record)}
          >
            <Trash2 size={14} strokeWidth={1.8} />
            <span style={{ fontSize: 12 }}>删除</span>
          </Button>
        </div>
      </div>
    </Card>
  );
}

export default DwLayerCard;
