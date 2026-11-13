import { ArrowLeft, ChevronRight, Pencil } from "lucide-react";
import { Button, Card, CardContent, CardHeader } from "@yak-ops/yak-ui";

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

function InfoItem({ label, value }: { label: string; value?: string }) {
  return (
    <div className="min-w-0">
      <div className="text-xs text-[var(--yak-components-muted-text)]">{label}</div>
      <div className="mt-1 break-words text-sm text-[var(--yak-components-panel-text)]">
        {value || "-"}
      </div>
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
    <div className="flex min-h-0 flex-1 flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
        <Button size="sm" variant="ghost" className="h-7 px-2" onClick={onBack}>
          <ArrowLeft size={14} />
          返回列表
        </Button>
        <ChevronRight size={13} />
        <span>数仓分层</span>
        {record?.cnName ? (
          <>
            <ChevronRight size={13} />
            <span className="max-w-[240px] truncate font-medium text-[var(--yak-components-panel-text)]">
              {record.cnName}
            </span>
          </>
        ) : null}
      </div>

      {loading ? (
        <div className="flex min-h-[220px] flex-1 items-center justify-center rounded-md border border-[var(--yak-components-panel-border)] bg-white text-xs text-[var(--yak-components-muted-text)]">
          正在加载分层详情...
        </div>
      ) : error || !record ? (
        <div className="flex min-h-[220px] flex-1 flex-col items-center justify-center gap-3 rounded-md border border-[var(--yak-components-panel-border)] bg-white text-xs text-[var(--yak-components-muted-text)]">
          <span>分层详情加载失败，请刷新后重试。</span>
          <Button size="sm" variant="secondary" onClick={onRetry}>重新加载</Button>
        </div>
      ) : (
        <Card>
          <CardHeader
            title="基本信息"
            description="查看当前数仓分层的标识、归属、模型类型及维护信息。"
            extra={
              <Button size="sm" variant="secondary" onClick={() => onEdit(record)}>
                <Pencil size={13} />
                编辑
              </Button>
            }
          />
          <CardContent className="space-y-6 p-5">
            <div className="grid grid-cols-1 gap-x-8 gap-y-5 sm:grid-cols-2 xl:grid-cols-4">
              <InfoItem label="英文缩写" value={record.enShortName} />
              <InfoItem label="英文名称" value={record.enName} />
              <InfoItem label="中文名称" value={record.cnName} />
              <InfoItem label="负责人" value={record.director} />
              <InfoItem
                label="分层归属"
                value={getDwTypeLabel(record.dwType, record.dwTypeName)}
              />
              <InfoItem label="模型类型" value={getDwModelTypeLabel(record.dwModelType)} />
              <InfoItem label="创建时间" value={record.createTime} />
              <InfoItem label="修改时间" value={record.updateTime} />
              <InfoItem label="创建人" value={record.createBy} />
              <InfoItem label="修改人" value={record.updateBy} />
            </div>

            <div className="border-t border-[var(--yak-components-panel-border)] pt-4">
              <InfoItem label="备注" value={record.remark} />
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

export default DwLayerDetail;
