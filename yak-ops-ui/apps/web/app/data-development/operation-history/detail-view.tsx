import { ClipboardList } from "lucide-react";
import type { AuditRecord } from "@/service/audit";

export function AuditDetailView({ record }: { record: AuditRecord }) {
  const fields = [
    ["操作类型", record.operation],
    ["对象类型", record.modelType],
    ["对象名称", record.modelName],
    ["操作人", record.userName],
    ["操作时间", record.createTime],
    ["耗时", record.latency],
  ];

  return (
    <div className="flex h-full min-h-0 min-w-0 flex-1 flex-col bg-white">
      <div className="flex shrink-0 items-center gap-2 border-b border-[#ececec] px-5 py-4">
        <ClipboardList size={17} className="text-[#5e6b82]" />
        <div className="text-[15px] font-medium text-[#24272c]">操作详情</div>
      </div>
      <div className="grid grid-cols-3 gap-px border-b border-[#ececec] bg-[#ececec]">
        {fields.map(([label, value]) => (
          <div key={label} className="bg-white px-4 py-3">
            <div className="text-[11px] text-[#8a919b]">{label}</div>
            <div className="mt-1 truncate text-[12px] text-[#30343a]">{value || "-"}</div>
          </div>
        ))}
      </div>
      <div className="min-h-0 flex-1 overflow-auto p-5">
        {record.description ? (
          <section>
            <div className="mb-2 text-[12px] font-medium text-[#30343a]">操作描述</div>
            <div className="whitespace-pre-wrap text-[12px] leading-6 text-[#5d6570]">
              {record.description}
            </div>
          </section>
        ) : null}
        <section className={record.description ? "mt-6" : ""}>
          <div className="mb-2 text-[12px] font-medium text-[#30343a]">详细信息</div>
          <pre className="whitespace-pre-wrap break-words rounded-[4px] bg-[#f7f8fa] p-3 text-[11px] leading-5 text-[#59616d]">
            {record.detail || "暂无详细信息"}
          </pre>
        </section>
      </div>
    </div>
  );
}