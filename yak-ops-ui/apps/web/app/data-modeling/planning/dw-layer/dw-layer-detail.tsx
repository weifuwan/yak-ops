import { ArrowLeft } from "lucide-react";
import { Button } from "@yak-ops/yak-ui";

import {
  getDwModelTypeLabel,
  getDwTypeLabel,
  type DwLayerRecord,
} from "./constants";

interface DwLayerDetailProps {
  record?: DwLayerRecord;
  loading: boolean;
  error: boolean;
  onBack: () => void;
  onEdit: (record: DwLayerRecord) => void;
  onRetry: () => void;
}

interface DescriptionItemProps {
  label: string;
  value?: string;
  className?: string;
  multiline?: boolean;
}

function DescriptionItem({
  label,
  value,
  className = "",
  multiline = false,
}: DescriptionItemProps) {
  return (
    <div className={"flex min-w-0 items-baseline " + className}>
      <span
        className="shrink-0"
        style={{
          color: "rgba(128, 128, 128, 1)",
          fontSize: 12,
          lineHeight: "22px",
        }}
      >
        {label}：
      </span>
      <span
        className={
          "min-w-0 pl-3 text-black " +
          (multiline ? "break-words whitespace-pre-wrap" : "break-words")
        }
        style={{
          fontSize: 12,
          lineHeight: "22px",
        }}
      >
        {value || "-"}
      </span>
    </div>
  );
}

export function DwLayerDetail({
  record,
  loading,
  error,
  onBack,
  onEdit,
  onRetry,
}: DwLayerDetailProps) {
  return (
    <div className="flex min-h-0 flex-1 flex-col bg-[#f2f3f5]">
      <header
        className="z-[9] shrink-0 bg-white px-6 py-4 shadow-[0_2px_8px_#0000000d]"
        style={{ boxSizing: "border-box" }}
      >
        <nav
          aria-label="Breadcrumb"
          className="flex items-center text-xs"
          style={{ lineHeight: "17px" }}
        >
          <button
            type="button"
            onClick={onBack}
            className="cursor-pointer border-0 bg-transparent p-0 text-[#1677ff] outline-none hover:text-[#0958d9]"
          >
            数仓分层
          </button>
          <span className="mx-2 text-[#bfbfbf]">/</span>
          <span className="font-medium text-[#595959]">{record?.cnName || "详情"}</span>
        </nav>

        <div className="mt-2 flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            isIconOnly
            aria-label="返回数仓分层列表"
            className="h-6 w-[42px] rounded-xl p-0"
            style={{
              width: 42,
              height: 24,
              borderRadius: 12,
              padding: 0,
              backgroundColor: "#fff",
              borderColor: "#d9d9d9",
            }}
            onClick={onBack}
          >
            <ArrowLeft size={14} />
          </Button>
          <h1
            className="m-0"
            style={{
              color: "rgba(38, 40, 44, 1)",
              fontSize: 20,
              fontWeight: 700,
              lineHeight: "28px",
            }}
          >
            {record?.cnName || "详情"}
          </h1>
        </div>
      </header>

      <main className="min-h-0 flex-1 overflow-y-auto" style={{ padding: 16 }}>
        {loading ? (
          <div className="flex min-h-[180px] items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">
            正在加载分层详情...
          </div>
        ) : error || !record ? (
          <div className="flex min-h-[180px] flex-col items-center justify-center gap-3 bg-white text-xs text-[var(--yak-components-muted-text)]">
            <span>分层详情加载失败，请刷新后重试。</span>
            <Button size="sm" variant="secondary" onClick={onRetry}>
              重新加载
            </Button>
          </div>
        ) : (
          <section
            aria-labelledby="dw-layer-basic-info-title"
            style={{
              backgroundColor: "#fff",
              paddingBottom: 8,
            }}
          >
            <div
              className="flex items-center justify-between"
              style={{ padding: 16 }}
            >
              <div className="flex items-center">
                <span
                  aria-hidden="true"
                  style={{
                    width: 4,
                    height: 16,
                    marginRight: 8,
                    backgroundColor: "#5664ff",
                  }}
                />
                <h2
                  id="dw-layer-basic-info-title"
                  className="m-0"
                  style={{
                    color: "#1e202d",
                    fontSize: 14,
                    fontWeight: 600,
                    lineHeight: "28px",
                  }}
                >
                  基本信息
                </h2>
              </div>

              <Button
                size="sm"
                variant="ghost"
                className="h-6 px-0 hover:bg-transparent"
                style={{
                  minWidth: 32,
                  padding: 0,
                  color: "#1677ff",
                  fontSize: 12,
                  fontWeight: 400,
                }}
                onClick={() => onEdit(record)}
              >
                编辑
              </Button>
            </div>

            <div
              style={{
                padding: "0 8px 8px 24px",
                marginBottom: 4,
              }}
            >
              <div className="grid grid-cols-1 gap-y-3 sm:grid-cols-2 xl:grid-cols-4">
                <DescriptionItem label="英文缩写" value={record.enShortName} />
                <DescriptionItem label="英文名称" value={record.enName} />
                <DescriptionItem label="中文名称" value={record.cnName} />
                <DescriptionItem label="负责人" value={record.director} />

                <DescriptionItem
                  label="分层归属"
                  value={getDwTypeLabel(record.dwType, record.dwTypeName)}
                />
                <DescriptionItem
                  label="模型类型"
                  value={getDwModelTypeLabel(record.dwModelType)}
                />

                <DescriptionItem
                  label="备注"
                  value={record.remark}
                  className="col-span-full"
                  multiline
                />

                <DescriptionItem
                  label="创建人"
                  value={record.createBy}
                  className="col-span-full sm:col-span-1"
                />
                <DescriptionItem
                  label="创建时间"
                  value={record.createTime}
                  className="col-span-full sm:col-span-1 xl:col-span-3"
                />
                <DescriptionItem
                  label="修改人"
                  value={record.updateBy}
                  className="col-span-full sm:col-span-1"
                />
                <DescriptionItem
                  label="修改时间"
                  value={record.updateTime}
                  className="col-span-full sm:col-span-1 xl:col-span-3"
                />
              </div>
            </div>
          </section>
        )}
      </main>
    </div>
  );
}

export default DwLayerDetail;
