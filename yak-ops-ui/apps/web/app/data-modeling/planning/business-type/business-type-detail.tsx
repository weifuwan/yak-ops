import { Pencil } from "lucide-react";
import { Button } from "@yak-ops/yak-ui";

import { PlanningIcon } from "../components/icons";
import BusinessTypeChildren from "./business-type-children";
import BusinessTypeDomainRelation from "./business-type-domain-relation";
import BusinessTypeDataMartManagement from "./business-type-data-mart";
import type { BusinessTypeRecord } from "./types";

interface Props {
  projectCode: string;
  record: BusinessTypeRecord;
  onEdit: () => void;
  onAddChild: () => void;
  onEditChild: (record: BusinessTypeRecord) => void;
  onDeleteChild: (record: BusinessTypeRecord) => void;
}

const CARD = "mb-4 rounded-md bg-white p-4 shadow-[0_1px_4px_rgba(0,0,0,0.08)]";
const TITLE = "mb-4 flex items-center gap-2 text-sm font-semibold text-[var(--yak-components-panel-text)]";
const display = (value?: string) => value && value.trim() ? value : "-";

function BusinessTypeInfo({ record, onEdit }: { record: BusinessTypeRecord; onEdit: () => void }) {
  const items = [
    ["英文缩写", record.enShortName], ["业务名称", record.businessName],
    ["创建人", record.createBy], ["创建时间", record.createTime],
    ["修改人", record.updateBy], ["修改时间", record.updateTime],
    ["模型评审人", record.reviewer], ["负责人", record.director],
  ];
  return (
    <section className={CARD}>
      <div className="mb-4 flex items-center justify-between">
        <h3 className={TITLE.replace("mb-4 ", "")}>
          <span className="h-4 w-1 rounded-full bg-[var(--yak-color-primary)]" />基本信息
        </h3>
        <Button size="sm" variant="ghost" className="text-xs" onClick={onEdit}>
          <Pencil size={13} />编辑
        </Button>
      </div>
      <div className="grid grid-cols-1 gap-x-8 gap-y-3 sm:grid-cols-2">
        {items.map(([label, value]) => (
          <div key={label} className="grid grid-cols-[84px_minmax(0,1fr)] gap-2 text-xs">
            <span className="text-[var(--yak-components-muted-text)]">{label}</span>
            <span className="break-words text-[var(--yak-components-panel-text)]">{display(value)}</span>
          </div>
        ))}
        <div className="grid grid-cols-[84px_minmax(0,1fr)] gap-2 text-xs sm:col-span-2">
          <span className="text-[var(--yak-components-muted-text)]">备注</span>
          <span className="whitespace-pre-wrap break-words text-[var(--yak-components-panel-text)]">{display(record.remark)}</span>
        </div>
      </div>
    </section>
  );
}

export function BusinessTypeDetail({
  projectCode, record, onEdit, onAddChild, onEditChild, onDeleteChild,
}: Props) {
  return (
    <>
      <header className="flex h-[72px] shrink-0 items-center border-b border-[var(--yak-components-panel-border)] bg-white px-5">
        <PlanningIcon name="business-type" size={20} className="mr-2 text-[var(--yak-color-primary)]" />
        <h1 className="truncate text-lg font-semibold text-[var(--yak-components-panel-text)]">{record.businessName}</h1>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
        <BusinessTypeInfo record={record} onEdit={onEdit} />
        <BusinessTypeChildren projectCode={projectCode} parent={record}
          onAdd={onAddChild} onEdit={onEditChild} onDelete={onDeleteChild} />
        <BusinessTypeDomainRelation projectCode={projectCode} businessTypeId={record.id || ""} />
        <BusinessTypeDataMartManagement projectCode={projectCode} businessTypeId={record.id || ""} />
      </div>
    </>
  );
}

export default BusinessTypeDetail;
