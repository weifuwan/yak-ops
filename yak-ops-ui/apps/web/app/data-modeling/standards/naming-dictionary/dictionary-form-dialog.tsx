import { useEffect, useState } from "react";
import { Button, Input, Modal } from "@yak-ops/yak-ui";

import type { DictionaryFormValues, DictionaryStandardRecord } from "./types";

export function DictionaryFormDialog({
  open, editing, saving, onClose, onSubmit,
}: {
  open: boolean; editing?: DictionaryStandardRecord; saving: boolean;
  onClose: () => void; onSubmit: (values: DictionaryFormValues) => void;
}) {
  const [values, setValues] = useState<DictionaryFormValues>({
    enShortName: "", enName: "", cnName: "",
  });
  const [error, setError] = useState("");
  const isSystem = editing?.flag === 1;

  useEffect(() => {
    if (!open) return;
    setValues({
      enShortName: editing?.enShortName || "",
      enName: editing?.enName || "",
      cnName: editing?.cnName || "",
    });
    setError("");
  }, [open, editing]);

  const update = (key: keyof DictionaryFormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setError("");
  };

  const submit = () => {
    const enShortName = values.enShortName.trim();
    const enName = values.enName.trim();
    const cnName = values.cnName.trim();
    if (!enShortName) return setError("请输入英文缩写");
    if (!enName) return setError("请输入英文名称");
    if (!cnName) return setError("请输入中文名称");
    if (enShortName.length > 64 || enName.length > 64 || cnName.length > 64) {
      return setError("名称长度不能超过 64 个字符");
    }
    onSubmit({ enShortName, enName, cnName });
  };

  return (
    <Modal
      open={open}
      centered
      width={620}
      title={editing ? "编辑命名词典" : "新建命名词典"}
      onClose={onClose}
      footer={<>
        <Button size="sm" variant="secondary" isDisabled={saving} onClick={onClose}>取消</Button>
        <Button size="sm" variant="primary" isPending={saving} isDisabled={isSystem} onClick={submit}>确定</Button>
      </>}
    >
      <div className="space-y-3">
        <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
          <label className="pt-1.5 text-xs text-[#475467]">英文缩写 <span className="text-[#d92d20]">*</span></label>
          <Input size="small" variant="outlined" maxLength={64} value={values.enShortName}
            disabled={isSystem} placeholder="请输入英文缩写" onChange={(event) => update("enShortName", event.target.value)} />
        </div>
        <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
          <label className="pt-1.5 text-xs text-[#475467]">英文名 <span className="text-[#d92d20]">*</span></label>
          <Input size="small" variant="outlined" maxLength={64} value={values.enName}
            disabled={isSystem} placeholder="请输入英文名" onChange={(event) => update("enName", event.target.value)} />
        </div>
        <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
          <label className="pt-1.5 text-xs text-[#475467]">中文名 <span className="text-[#d92d20]">*</span></label>
          <Input size="small" variant="outlined" maxLength={64} value={values.cnName}
            disabled={isSystem} placeholder="请输入中文名" onChange={(event) => update("cnName", event.target.value)} />
        </div>
        {isSystem ? (
          <p className="pl-[116px] text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
            系统内置词条不可修改
          </p>
        ) : null}
        {error ? <p role="alert" className="pl-[116px] text-xs text-[#d92d20]">{error}</p> : null}
      </div>
    </Modal>
  );
}

export default DictionaryFormDialog;
