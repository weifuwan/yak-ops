import { useEffect, useState } from "react";
import { Button, Input, Modal, Textarea } from "@yak-ops/yak-ui";

import type { BusinessTypeFormValues, BusinessTypeRecord } from "./types";
import { EMPTY_FORM } from "./types";

export function BusinessTypeFormDialog({
  open, record, parentId, saving, onClose, onSubmit,
}: {
  open: boolean; record?: BusinessTypeRecord; parentId?: string; saving: boolean;
  onClose: () => void; onSubmit: (values: BusinessTypeFormValues) => void;
}) {
  const [values, setValues] = useState<BusinessTypeFormValues>(EMPTY_FORM);
  const [error, setError] = useState("");
  useEffect(() => {
    setValues({
      businessName: record?.businessName || "", enShortName: record?.enShortName || "",
      reviewer: record?.reviewer || "", director: record?.director || "", remark: record?.remark || "",
    });
    setError("");
  }, [record, open]);
  const update = (key: keyof BusinessTypeFormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    if (error) setError("");
  };
  const submit = () => {
    if (!values.businessName.trim()) return setError("请输入中文名");
    if (values.businessName.trim().length > 64) return setError("中文名不能超过 64 个字符");
    if (!values.enShortName.trim()) return setError("请输入英文缩写");
    if (values.enShortName.trim().length > 64) return setError("英文缩写不能超过 64 个字符");
    if (!values.director.trim()) return setError("请输入负责人");
    if (values.director.trim().length > 32) return setError("负责人不能超过 32 个字符");
    if (values.reviewer.length > 32) return setError("模型评审人不能超过 32 个字符");
    if (values.remark.length > 2048) return setError("备注不能超过 2048 个字符");
    onSubmit({
      businessName: values.businessName.trim(), enShortName: values.enShortName.trim(),
      reviewer: values.reviewer.trim(), director: values.director.trim(), remark: values.remark.trim(),
    });
  };
  const field = (label: string, key: keyof BusinessTypeFormValues, required = false, multiline = false) => (
    <label className="grid grid-cols-[112px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-2 text-right text-xs text-[var(--yak-components-muted-text)]">
        {required ? <span className="mr-1 text-[#d92d20]">*</span> : null}{label}
      </span>
      {multiline ? (
        <Textarea size="small" variant="outlined" rows={3} value={values[key]}
          onValueChange={(value) => update(key, String(value))} placeholder={label} />
      ) : (
        <Input size="small" variant="outlined" value={values[key]}
          onValueChange={(value) => update(key, String(value))} placeholder={label} />
      )}
    </label>
  );
  return (
    <Modal open={open} centered width={680} title={record?.id ? "编辑业务分类" : "新增业务分类"}
      onClose={onClose}
      footer={<>
        <Button size="sm" variant="secondary" isDisabled={saving} onClick={onClose}>取消</Button>
        <Button size="sm" variant="primary" isPending={saving} onClick={submit}>确定</Button>
      </>}>
      <div className="space-y-4">
        {field("英文缩写", "enShortName", true)}
        {field("中文名", "businessName", true)}
        {field("模型评审人", "reviewer")}
        {field("负责人", "director", true)}
        {field("备注", "remark", false, true)}
        {parentId && !record?.id ? <div className="pl-[124px] text-[11px] text-[var(--yak-components-muted-text)]">将作为所选业务分类的子级创建</div> : null}
        {error ? <div role="alert" className="pl-[124px] text-xs text-[#d92d20]">{error}</div> : null}
      </div>
    </Modal>
  );
}

export default BusinessTypeFormDialog;
