import { Button, Table } from "@yak-ops/yak-ui";
import { X } from "lucide-react";

import type { ModelingVersionRecord } from "../../../../service";
import { PanelResizer } from "../../../../data-development/layout/panel-resizer";

type IndicatorVersionPanelProps = {
  width: number;
  versions: ModelingVersionRecord[];
  loading: boolean;
  onResize: (width: number) => void;
  onClose: () => void;
  onRollback: (version: ModelingVersionRecord) => void;
};

export function IndicatorVersionPanel({ width, versions, loading, onResize, onClose, onRollback }: IndicatorVersionPanelProps) {
  return (
    <>
      <PanelResizer width={width} minWidth={280} maxWidth={520} resizeDirection="left" ariaLabel="调整版本管理面板宽度" onResize={onResize} />
      <section className="flex h-full min-h-0 max-w-[calc(100vw-64px)] flex-col border-l border-[#e5e7eb] bg-white" style={{ width }}>
        <header className="flex h-11 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-3">
          <span className="text-sm font-semibold text-[#303846]">版本管理</span>
          <button type="button" title="收起面板" aria-label="收起版本管理面板" onClick={onClose} className="flex size-6 cursor-pointer items-center justify-center rounded text-[#8b93a1] transition-colors hover:bg-[#f0f2f5] hover:text-[#303846]">
            <X size={14} />
          </button>
        </header>
        <div className="min-h-0 flex-1 overflow-y-auto bg-white p-2">
          <Table<ModelingVersionRecord>
            columns={[
              { key: "versionNumber", title: "版本", width: 56, render: (_value, record) => "V" + record.versionNumber },
              { key: "updateTime", title: "提交时间", width: 118, render: (_value, record) => record.updateTime || "-" },
              { key: "updateBy", title: "提交人", width: 70, render: (_value, record) => record.updateBy || "-" },
              { key: "actions", title: "操作", width: 56, align: "center", render: (_value, record) => <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => onRollback(record)}>回滚</Button> },
            ]}
            dataSource={versions}
            rowKey={(record) => record.id}
            loading={loading}
            emptyText="暂无已提交版本"
            pagination={false}
            size="small"
            scroll={{ y: "calc(100vh - 220px)" }}
          />
        </div>
      </section>
    </>
  );
}
