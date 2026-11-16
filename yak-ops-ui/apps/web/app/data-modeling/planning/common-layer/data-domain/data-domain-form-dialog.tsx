import { useEffect, useState } from "react";
import { Button, Field, FieldLabel, Input, Modal, Textarea } from "@yak-ops/yak-ui";

import { EMPTY_DATA_DOMAIN_FORM, isSystemRecord, type DataDomainFormValues, type DataDomainRecord } from "./types";

interface Props {
  open: boolean;
  editing?: DataDomainRecord;
  saving: boolean;
  onClose: () => void;
  onSubmit: (values: DataDomainFormValues) => void;
}

export function DataDomainFormDialog({ open, editing, saving, onClose, onSubmit }: Props) {
  const [values, setValues] = useState<DataDomainFormValues>(EMPTY_DATA_DOMAIN_FORM);
  const [error, setError] = useState("");
  const system = isSystemRecord(editing);

  useEffect(() => {
    if (!open) return;
    setValues(editing ? {
      enShortName: editing.enShortName || "",
      enName: editing.enName || "",
      cnName: editing.cnName || "",
      reviewer: editing.reviewer || "",
      director: editing.director || "",
      remark: editing.remark || "",
    } : EMPTY_DATA_DOMAIN_FORM);
    setError("");
  }, [open, editing]);

  const update = (key: keyof DataDomainFormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setError("");
  };

  const submit = () => {
    const shortName = values.enShortName.trim();
    const englishName = values.enName.trim();
    const chineseName = values.cnName.trim();
    if (!shortName) return setError("请输入英文缩写");
    if (shortName.length > 64 || !/^[a-z0-9](?:[a-z0-9_]*[a-z0-9])?$/.test(shortName)) {
      return setError("英文缩写请使用小写字母、数字和下划线，且不能以下划线结尾");
    }
    if (!englishName) return setError("请输入英文名");
    if (englishName.length > 64 || !/^[a-zA-Z0-9](?:[_&a-zA-Z0-9\s]*[_&a-zA-Z0-9])?$/.test(englishName)) {
      return setError("英文名请以字母或数字开头，支持下划线、&和空格");
    }
    if (!chineseName) return setError("请输入中文名");
    if (chineseName.length > 64 || !/^[a-zA-Z0-9\u4e00-\u9fa5](?:[_&a-zA-Z0-9\u4e00-\u9fa5]*)$/.test(chineseName)) {
      return setError("中文名格式不正确，请检查特殊字符");
    }
    if (!values.director.trim()) return setError("请输入负责人");
    if (values.director.trim().length > 32) return setError("负责人不能超过 32 个字符");
    if (values.reviewer.length > 32) return setError("模型评审人不能超过 32 个字符");
    if (values.remark.length > 2048) return setError("备注不能超过 2048 个字符");

    onSubmit({
      enShortName: shortName,
      enName: englishName,
      cnName: chineseName,
      reviewer: values.reviewer.trim(),
      director: values.director.trim(),
      remark: values.remark.trim(),
    });
  };

  return (
    <Modal open={open} centered width={630} title={editing ? "编辑数据域" : "新建数据域"} onClose={onClose}
      footer={<>
        <Button size="sm" variant="secondary" isDisabled={saving} onClick={onClose}>取消</Button>
        <Button size="sm" variant="primary" isPending={saving} onClick={submit}>确定</Button>
      </>}>
      <div className="space-y-3">
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required className="pt-1.5 text-xs">英文缩写</FieldLabel>
          <Input size="small" variant="outlined" maxLength={64} value={values.enShortName} disabled={system} placeholder="例如：crm"
            onChange={(event) => update("enShortName", event.target.value)} />
        </Field>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required className="pt-1.5 text-xs">英文名</FieldLabel>
          <Input size="small" variant="outlined" maxLength={64} value={values.enName} disabled={system} placeholder="请输入英文名"
            onChange={(event) => update("enName", event.target.value)} />
        </Field>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required className="pt-1.5 text-xs">中文名</FieldLabel>
          <Input size="small" variant="outlined" maxLength={64} value={values.cnName} disabled={system} placeholder="请输入中文名"
            onChange={(event) => update("cnName", event.target.value)} />
        </Field>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel className="pt-1.5 text-xs">模型评审人</FieldLabel>
          <Input size="small" variant="outlined" maxLength={32} value={values.reviewer} placeholder="请输入模型评审人"
            onChange={(event) => update("reviewer", event.target.value)} />
        </Field>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required className="pt-1.5 text-xs">负责人</FieldLabel>
          <Input size="small" variant="outlined" maxLength={32} value={values.director} placeholder="请输入负责人"
            onChange={(event) => update("director", event.target.value)} />
        </Field>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel className="pt-1.5 text-xs">备注</FieldLabel>
          <Textarea size="small" variant="outlined" rows={3} maxLength={2048} value={values.remark} disabled={system} placeholder="请输入备注"
            onValueChange={(value) => update("remark", String(value || ""))} />
        </Field>
        {system ? <p className="pl-[116px] text-[11px] leading-5 text-[var(--yak-components-muted-text)]">系统默认数据域的英文缩写、英文名、中文名和备注不可修改</p> : null}
        {error ? <p role="alert" className="pl-[116px] text-xs text-[#d92d20]">{error}</p> : null}
      </div>
    </Modal>
  );
}

export default DataDomainFormDialog;
