import { ArrowLeft } from "lucide-react";
import { Button } from "@yak-ops/yak-ui";

import type { BusinessProcessRecord } from "./types";

interface Props {
  record: BusinessProcessRecord;
  onBack: () => void;
  onEdit: (record: BusinessProcessRecord) => void;
}

function SectionTitle({ children }: { children: string }) {
  return (
    <div className="flex items-center gap-2 text-sm font-semibold text-[#20232b]">
      <span className="h-4 w-1 rounded-sm bg-[#5664ff]" />
      {children}
    </div>
  );
}

function DescriptionItem({
  label, value, className = "", multiline = false,
}: {
  label: string;
  value?: string;
  className?: string;
  multiline?: boolean;
}) {
  return (
    <div className={"flex min-w-0 items-baseline " + className}>
      <span className="shrink-0 text-xs leading-[22px] text-[rgba(128,128,128,1)]">{label}：</span>
      <span className={"min-w-0 pl-3 text-xs leading-[22px] text-black " + (multiline ? "whitespace-pre-wrap break-words" : "break-words")}>
        {value || "-"}
      </span>
    </div>
  );
}

function BasicInfoSection({
  record, onEdit,
}: {
  record: BusinessProcessRecord;
  onEdit: () => void;
}) {
  return (
    <section className="bg-white pb-2">
      <div className="flex items-center justify-between p-4">
        <SectionTitle>基本信息</SectionTitle>
        <Button size="sm" variant="ghost" className="h-6 min-w-8 px-0 text-xs text-[#1677ff] hover:bg-transparent" onClick={onEdit}>
          编辑
        </Button>
      </div>
      <div className="mb-1 grid grid-cols-1 gap-x-8 gap-y-2 px-6 pb-2 sm:grid-cols-2 xl:grid-cols-4">
        <DescriptionItem label="英文缩写" value={record.enShortName} />
        <DescriptionItem label="英文名称" value={record.enName} />
        <DescriptionItem label="中文名称" value={record.cnName} className="xl:col-span-2" />
        <DescriptionItem label="负责人" value={record.director} className="xl:col-span-2" />
        <DescriptionItem label="数据域" value={record.dataDomainCnName} className="xl:col-span-2" />
        <DescriptionItem label="备注" value={record.remark} className="col-span-full" multiline />
        <DescriptionItem label="创建人" value={record.createBy} />
        <DescriptionItem label="创建时间" value={record.createTime} className="sm:col-span-1 xl:col-span-3" />
        <DescriptionItem label="修改人" value={record.updateBy} />
        <DescriptionItem label="修改时间" value={record.updateTime} className="sm:col-span-1 xl:col-span-3" />
      </div>
    </section>
  );
}

export function BusinessProcessDetail({ record, onBack, onEdit }: Props) {
  return (
    <div className="flex min-h-0 flex-1 flex-col bg-[#f2f3f5]">
      <header className="z-[9] shrink-0 bg-white px-5 py-4 shadow-[0_2px_8px_#0000000d]">
        <nav aria-label="Breadcrumb" className="flex items-center text-xs leading-[17px]">
          <button type="button" onClick={onBack} className="cursor-pointer border-0 bg-transparent p-0 text-[#1677ff] hover:text-[#0958d9]">
            业务过程
          </button>
          <span className="mx-2 text-[#bfbfbf]">/</span>
          <span className="font-medium text-[#595959]">{record.cnName || "详情"}</span>
        </nav>
        <div className="mt-2 flex items-center gap-2">
          <Button size="sm" variant="outline" isIconOnly aria-label="返回业务过程列表"
            className="h-6 w-[42px] rounded-xl p-0" onClick={onBack}>
            <ArrowLeft size={14} />
          </Button>
          <h1 className="m-0 text-xl font-bold leading-7 text-[#26282c]">{record.cnName || "业务过程详情"}</h1>
        </div>
      </header>
      <main className="min-h-0 flex-1 overflow-y-auto p-4">
        <BasicInfoSection record={record} onEdit={() => onEdit(record)} />
      </main>
    </div>
  );
}

export default BusinessProcessDetail;
