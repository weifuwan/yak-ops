import { Button, Modal } from "@yak-ops/yak-ui";

import { isSystemRecord, type BusinessProcessRecord } from "./types";

interface Props {
  record?: BusinessProcessRecord;
  onClose: () => void;
}

const fields: Array<[string, keyof BusinessProcessRecord]> = [
  ["英文缩写", "enShortName"],
  ["英文名称", "enName"],
  ["中文名称", "cnName"],
  ["所属数据域", "dataDomainCnName"],
  ["负责人", "director"],
  ["创建人", "createBy"],
  ["创建时间", "createTime"],
  ["修改人", "updateBy"],
  ["修改时间", "updateTime"],
  ["备注", "remark"],
];

export function BusinessProcessViewDialog({ record, onClose }: Props) {
  return (
    <Modal open={Boolean(record)} centered width={680} title="业务过程详情" onClose={onClose}
      footer={<Button size="sm" variant="secondary" onClick={onClose}>关闭</Button>}>
      {record ? (
        <div className="grid grid-cols-1 gap-x-8 gap-y-4 sm:grid-cols-2">
          {fields.map(([label, key]) => (
            <div key={key} className={key === "remark" ? "sm:col-span-2" : ""}>
              <div className="mb-1 text-xs text-[var(--yak-components-muted-text)]">{label}</div>
              <div className="break-words whitespace-pre-wrap text-sm text-[var(--yak-components-panel-text)]">
                {String(record[key] ?? "-") || "-"}
                {key === "enShortName" && isSystemRecord(record) ? (
                  <span className="ml-2 rounded border border-[#91caff] bg-[#e6f4ff] px-1.5 py-0.5 text-[10px] text-[#1677ff]">系统默认</span>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </Modal>
  );
}

export default BusinessProcessViewDialog;
