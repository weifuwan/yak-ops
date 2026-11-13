import { useEffect, useState } from "react";
import {
  Button, Field, FieldLabel, Input, Modal, Select, SelectContent, SelectItem,
  SelectItemText, SelectTrigger, SelectValue, Textarea,
} from "@yak-ops/yak-ui";

import type { ModelingRecord } from "../../service";
import {
  EMPTY_DATA_MART_FORM, MART_TYPE_OPTIONS, type DataMartFormValues, type DataMartRecord,
} from "./types";

interface BusinessTypeOption extends ModelingRecord {
  id?: string;
  businessName?: string;
}

interface Props {
  open: boolean;
  editing?: DataMartRecord;
  parentId?: string;
  businessTypes: BusinessTypeOption[];
  businessTypesLoading?: boolean;
  businessTypesError?: boolean;
  onRetryBusinessTypes?: () => void;
  saving: boolean;
  onClose: () => void;
  onSubmit: (values: DataMartFormValues) => void;
}

const FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

function getMartType(value: DataMartRecord["martType"]): string {
  if (typeof value === "string") return value;
  return value?.code || "";
}

export function DataMartFormDialog({
  open, editing, parentId, businessTypes, businessTypesLoading = false, businessTypesError = false, onRetryBusinessTypes, saving, onClose, onSubmit,
}: Props) {
  const [values, setValues] = useState<DataMartFormValues>(EMPTY_DATA_MART_FORM);
  const [error, setError] = useState("");
  const isChild = Boolean(parentId || editing?.parentId);

  useEffect(() => {
    if (!open) return;
    setValues(editing ? {
      enShortName: editing.enShortName || "",
      martName: editing.martName || "",
      martType: getMartType(editing.martType),
      businessTypeId: editing.businessTypeId || "",
      director: editing.director || "",
      remark: editing.remark || "",
    } : EMPTY_DATA_MART_FORM);
    setError("");
  }, [open, editing]);

  const update = (key: keyof DataMartFormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setError("");
  };

  const submit = () => {
    const enShortName = values.enShortName.trim();
    const martName = values.martName.trim();
    const director = values.director.trim();
    if (!enShortName) return setError("请输入英文缩写");
    if (enShortName.length > 64) return setError("英文缩写不能超过 64 个字符");
    if (!martName) return setError("请输入集市名称");
    if (martName.length > 64) return setError("集市名称不能超过 64 个字符");
    if (!director) return setError("请输入负责人");
    if (director.length > 32) return setError("负责人不能超过 32 个字符");
    if (values.remark.length > 2048) return setError("备注不能超过 2048 个字符");
    onSubmit({
      enShortName,
      martName,
      martType: values.martType,
      businessTypeId: isChild ? (editing?.businessTypeId || "") : values.businessTypeId,
      director,
      remark: values.remark.trim(),
    });
  };

  return (
    <Modal
      open={open}
      centered
      width={640}
      title={editing ? "编辑数据集市" : isChild ? "新建子数据集市" : "新建数据集市"}
      onClose={onClose}
      footer={<>
        <Button size="sm" variant="secondary" isDisabled={saving} onClick={onClose}>取消</Button>
        <Button size="sm" variant="primary" isPending={saving} onClick={submit}>确定</Button>
      </>}
    >
      <div className="space-y-3">
        <Field className={FIELD_CLASS}>
          <FieldLabel required className="pt-1.5 text-xs">英文缩写</FieldLabel>
          <Input size="small" variant="outlined" maxLength={64} value={values.enShortName}
            placeholder="请输入英文缩写" onChange={(event) => update("enShortName", event.target.value)} />
        </Field>
        <Field className={FIELD_CLASS}>
          <FieldLabel required className="pt-1.5 text-xs">集市名称</FieldLabel>
          <Input size="small" variant="outlined" maxLength={64} value={values.martName}
            placeholder="请输入集市名称" onChange={(event) => update("martName", event.target.value)} />
        </Field>
        <Field className={FIELD_CLASS}>
          <FieldLabel className="pt-1.5 text-xs">集市类型</FieldLabel>
          <Select value={values.martType || undefined} onValueChange={(value) => update("martType", String(value || ""))}>
            <SelectTrigger size="small" variant="outlined" className="w-full">
              <SelectValue placeholder="请选择集市类型" />
            </SelectTrigger>
            <SelectContent emptyContent="暂无集市类型">
              {MART_TYPE_OPTIONS.map((item) => (
                <SelectItem key={item.value} value={item.value}>
                  <SelectItemText>{item.label}</SelectItemText>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>
        {!isChild ? (
          <Field className={FIELD_CLASS}>
            <FieldLabel className="pt-1.5 text-xs">所属业务分类</FieldLabel>
            <Select value={values.businessTypeId || undefined}
              disabled={(businessTypesLoading && businessTypes.length === 0) || (businessTypesError && businessTypes.length === 0)}
              onValueChange={(value) => update("businessTypeId", String(value || ""))}>
              <SelectTrigger size="small" variant="outlined" className="w-full">
                <SelectValue placeholder="请选择业务分类" />
              </SelectTrigger>
              <SelectContent emptyContent={businessTypesError ? "业务分类加载失败，请重试" : businessTypesLoading ? "正在加载业务分类..." : "暂无业务分类"}>
                {businessTypes.filter((item) => item.id).map((item) => (
                  <SelectItem key={item.id} value={item.id || ""}>
                    <SelectItemText>{item.businessName || "-"}</SelectItemText>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {businessTypesError ? (
              <div className="mt-1 flex items-center justify-between gap-2 text-[11px] text-[#d92d20]">
                <span>{businessTypes.length ? "业务分类刷新失败，当前显示已加载的数据" : "业务分类加载失败，暂时无法选择"}</span>
                {onRetryBusinessTypes ? <Button size="sm" variant="ghost" onClick={onRetryBusinessTypes}>重试</Button> : null}
              </div>
            ) : null}
          </Field>
        ) : null}
        <Field className={FIELD_CLASS}>
          <FieldLabel required className="pt-1.5 text-xs">负责人</FieldLabel>
          <Input size="small" variant="outlined" maxLength={32} value={values.director}
            placeholder="请输入负责人" onChange={(event) => update("director", event.target.value)} />
        </Field>
        <Field className={FIELD_CLASS}>
          <FieldLabel className="pt-1.5 text-xs">备注</FieldLabel>
          <Textarea size="small" variant="outlined" rows={3} maxLength={2048} value={values.remark}
            placeholder="请输入备注" onValueChange={(value) => update("remark", String(value || ""))} />
        </Field>
        {isChild ? (
          <p className="pl-[116px] text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
            子数据集市将自动归入当前选中的上级集市
          </p>
        ) : null}
        {error ? <p role="alert" className="pl-[116px] text-xs text-[#d92d20]">{error}</p> : null}
      </div>
    </Modal>
  );
}

export default DataMartFormDialog;
