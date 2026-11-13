import { useEffect, useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import {
  Button, Field, FieldLabel, Input, Modal, Select, SelectContent, SelectItem,
  SelectItemText, SelectTrigger, SelectValue, Textarea,
} from "@yak-ops/yak-ui";

import type {
  CodeDetailRecord, CodeStandardRecord, FieldStandardRecord, StandardModule,
  UnitStandardRecord,
} from "./types";

type RecordValue = FieldStandardRecord | CodeStandardRecord | UnitStandardRecord;
interface DetailValue {
  key: string;
  enShortName: string;
  enName: string;
  cnName: string;
  remark: string;
}
interface FormValues {
  stdCode: string;
  enShortName: string;
  enName: string;
  cnName: string;
  fieldDataType: string;
  fieldLength: string;
  fieldPrecision: string;
  fieldCanNull: string;
  fieldDefaultValue: string;
  businessDefinition: string;
  parentStd: string;
  reference: string;
  remark: string;
}

interface Props {
  open: boolean;
  module: StandardModule;
  parentId?: string;
  editing?: RecordValue;
  saving: boolean;
  onClose: () => void;
  onSubmit: (values: Record<string, unknown>) => void;
}

const FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";
const DATA_TYPES = [
  "INT", "BIGINT", "BOOLEAN", "STRING", "TEXT", "DECIMAL", "DATE", "DATETIME",
  "TIMESTAMP", "JSON", "ARRAY", "DOUBLE", "MAP", "STRUCT",
].map((value) => ({ value, label: value }));

function createDetail(item?: CodeDetailRecord, index = 0): DetailValue {
  return {
    key: item?.id || "new-" + index,
    enShortName: item?.enShortName || "",
    enName: item?.enName || "",
    cnName: item?.cnName || "",
    remark: item?.remark || "",
  };
}

export function StandardRecordFormDialog({
  open, module, parentId, editing, saving, onClose, onSubmit,
}: Props) {
  const [values, setValues] = useState<FormValues>({
    stdCode: "", enShortName: "", enName: "", cnName: "", fieldDataType: "STRING",
    fieldLength: "", fieldPrecision: "", fieldCanNull: "true", fieldDefaultValue: "",
    businessDefinition: "", parentStd: "", reference: "", remark: "",
  });
  const [details, setDetails] = useState<DetailValue[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!open) return;
    const item = editing as RecordValue | undefined;
    const field = module === "FIELD" ? item as FieldStandardRecord | undefined : undefined;
    const code = module === "CODE" ? item as CodeStandardRecord | undefined : undefined;
    setValues({
      stdCode: field?.stdCode || "",
      enShortName: item?.enShortName || "",
      enName: item?.enName || "",
      cnName: item?.cnName || "",
      fieldDataType: field?.fieldDataType || "STRING",
      fieldLength: field?.fieldLength == null ? "" : String(field.fieldLength),
      fieldPrecision: field?.fieldPrecision == null ? "" : String(field.fieldPrecision),
      fieldCanNull: field?.fieldCanNull === false ? "false" : "true",
      fieldDefaultValue: field?.fieldDefaultValue || "",
      businessDefinition: field?.businessDefinition || "",
      parentStd: field?.parentStd || "",
      reference: field?.reference || "",
      remark: code?.remark || "",
    });
    setDetails((code?.detailList || []).map((detail, index) => createDetail(detail, index)));
    setError("");
  }, [open, module, editing]);

  const update = (key: keyof FormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setError("");
  };

  const updateDetail = (index: number, key: keyof Omit<DetailValue, "key">, value: string) => {
    setDetails((current) => current.map((item, itemIndex) =>
      itemIndex === index ? { ...item, [key]: value } : item));
    setError("");
  };

  const submit = () => {
    const enShortName = values.enShortName.trim();
    const enName = values.enName.trim();
    const cnName = values.cnName.trim();
    if (!enShortName) return setError("请输入英文缩写");
    if (!enName) return setError("请输入英文名称");
    if (!cnName) return setError("请输入中文名称");

    if (module === "FIELD") {
      if (!values.stdCode.trim()) return setError("请输入标准编码");
      if (!values.fieldDataType) return setError("请选择数据类型");
      onSubmit({
        ...(editing?.id ? { id: editing.id } : {}),
        parentId: parentId ?? editing?.parentId ?? null,
        stdCode: values.stdCode.trim(),
        enShortName, enName, cnName,
        fieldDataType: values.fieldDataType,
        fieldLength: values.fieldLength.trim() ? Number(values.fieldLength) : null,
        fieldPrecision: values.fieldPrecision.trim() ? Number(values.fieldPrecision) : null,
        fieldCanNull: values.fieldCanNull === "true",
        fieldDefaultValue: values.fieldDefaultValue.trim(),
        businessDefinition: values.businessDefinition.trim(),
        parentStd: values.parentStd.trim(),
        reference: values.reference.trim(),
      });
      return;
    }

    if (module === "CODE") {
      const detailList = details
        .filter((item) => item.enShortName.trim() || item.enName.trim() || item.cnName.trim() || item.remark.trim())
        .map((item) => ({
          ...(item.key.startsWith("new-") ? {} : { id: item.key }),
          enShortName: item.enShortName.trim(),
          enName: item.enName.trim(),
          cnName: item.cnName.trim(),
          remark: item.remark.trim(),
        }));
      if (detailList.some((item) => !item.enShortName || !item.enName || !item.cnName)) {
        return setError("代码明细的编码取值、英文名称和中文名称不能为空");
      }
      onSubmit({
        ...(editing?.id ? { id: editing.id } : {}),
        parentId: parentId ?? editing?.parentId ?? null,
        enShortName, enName, cnName,
        remark: values.remark.trim(),
        detailList,
      });
      return;
    }

    onSubmit({
      ...(editing?.id ? { id: editing.id } : {}),
      parentId: parentId ?? editing?.parentId ?? null,
      enShortName, enName, cnName,
    });
  };

  const isField = module === "FIELD";
  const isCode = module === "CODE";
  const title = isField ? "字段标准" : isCode ? "标准代码" : "度量单位";

  return (
    <Modal
      open={open}
      centered
      width={isCode ? 920 : 680}
      title={(editing ? "编辑" : "新建") + title}
      onClose={onClose}
      footer={<>
        <Button size="sm" variant="secondary" isDisabled={saving} onClick={onClose}>取消</Button>
        <Button size="sm" variant="primary" isPending={saving} onClick={submit}>确定</Button>
      </>}
    >
      <div className="max-h-[68vh] space-y-3 overflow-y-auto pr-1">
        {isField ? (
          <Field className={FIELD_CLASS}>
            <FieldLabel required className="pt-1.5 text-xs">标准编码</FieldLabel>
            <Input size="small" variant="outlined" maxLength={64} value={values.stdCode}
              placeholder="请输入标准编码" onChange={(event) => update("stdCode", event.target.value)} />
          </Field>
        ) : null}
        <Field className={FIELD_CLASS}>
          <FieldLabel required className="pt-1.5 text-xs">英文缩写</FieldLabel>
          <Input size="small" variant="outlined" maxLength={128} value={values.enShortName}
            placeholder="请输入英文缩写" onChange={(event) => update("enShortName", event.target.value)} />
        </Field>
        <Field className={FIELD_CLASS}>
          <FieldLabel required className="pt-1.5 text-xs">英文名称</FieldLabel>
          <Input size="small" variant="outlined" maxLength={2048} value={values.enName}
            placeholder="请输入英文名称" onChange={(event) => update("enName", event.target.value)} />
        </Field>
        <Field className={FIELD_CLASS}>
          <FieldLabel required className="pt-1.5 text-xs">中文名称</FieldLabel>
          <Input size="small" variant="outlined" maxLength={2048} value={values.cnName}
            placeholder="请输入中文名称" onChange={(event) => update("cnName", event.target.value)} />
        </Field>

        {isField ? (
          <>
            <Field className={FIELD_CLASS}>
              <FieldLabel required className="pt-1.5 text-xs">数据类型</FieldLabel>
              <Select value={values.fieldDataType} onValueChange={(value) => update("fieldDataType", String(value || ""))}>
                <SelectTrigger size="small" variant="outlined" className="w-full">
                  <SelectValue placeholder="请选择数据类型" />
                </SelectTrigger>
                <SelectContent>
                  {DATA_TYPES.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      <SelectItemText>{item.label}</SelectItemText>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
                <FieldLabel className="pt-1.5 text-xs">字段长度</FieldLabel>
                <Input size="small" variant="outlined" value={values.fieldLength} placeholder="例如 64"
                  onChange={(event) => update("fieldLength", event.target.value)} />
              </Field>
              <Field className="grid grid-cols-[64px_minmax(0,1fr)] items-start gap-3">
                <FieldLabel className="pt-1.5 text-xs">字段精度</FieldLabel>
                <Input size="small" variant="outlined" value={values.fieldPrecision} placeholder="例如 2"
                  onChange={(event) => update("fieldPrecision", event.target.value)} />
              </Field>
            </div>
            <Field className={FIELD_CLASS}>
              <FieldLabel className="pt-1.5 text-xs">允许为空</FieldLabel>
              <Select value={values.fieldCanNull} onValueChange={(value) => update("fieldCanNull", String(value || "true"))}>
                <SelectTrigger size="small" variant="outlined" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="true"><SelectItemText>是</SelectItemText></SelectItem>
                  <SelectItem value="false"><SelectItemText>否</SelectItemText></SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field className={FIELD_CLASS}>
              <FieldLabel className="pt-1.5 text-xs">默认值</FieldLabel>
              <Input size="small" variant="outlined" value={values.fieldDefaultValue}
                placeholder="请输入默认值" onChange={(event) => update("fieldDefaultValue", event.target.value)} />
            </Field>
            <Field className={FIELD_CLASS}>
              <FieldLabel className="pt-1.5 text-xs">父级标准</FieldLabel>
              <Input size="small" variant="outlined" value={values.parentStd}
                placeholder="请输入父级标准" onChange={(event) => update("parentStd", event.target.value)} />
            </Field>
            <Field className={FIELD_CLASS}>
              <FieldLabel className="pt-1.5 text-xs">引用代码</FieldLabel>
              <Input size="small" variant="outlined" value={values.reference}
                placeholder="请输入引用代码" onChange={(event) => update("reference", event.target.value)} />
            </Field>
            <Field className={FIELD_CLASS}>
              <FieldLabel className="pt-1.5 text-xs">业务定义</FieldLabel>
              <Textarea size="small" variant="outlined" rows={3} value={values.businessDefinition}
                placeholder="请输入业务定义" onValueChange={(value) => update("businessDefinition", String(value || ""))} />
            </Field>
          </>
        ) : null}

        {isCode ? (
          <>
            <Field className={FIELD_CLASS}>
              <FieldLabel className="pt-1.5 text-xs">描述</FieldLabel>
              <Textarea size="small" variant="outlined" rows={2} value={values.remark}
                placeholder="请输入描述" onValueChange={(value) => update("remark", String(value || ""))} />
            </Field>
            <div className="rounded-md border border-[#e7e9ee]">
              <div className="flex items-center justify-between border-b border-[#e7e9ee] px-3 py-2">
                <span className="text-xs font-semibold text-[#344054]">代码明细</span>
                <Button size="sm" variant="secondary" onClick={() => setDetails((current) => [...current, createDetail(undefined, current.length)])}>
                  <Plus size={13} />新增明细
                </Button>
              </div>
              {details.length ? (
                <div className="space-y-2 p-3">
                  {details.map((item, index) => (
                    <div key={item.key} className="grid grid-cols-[1fr_1fr_1fr_1.2fr_28px] items-center gap-2">
                      <Input size="small" variant="outlined" value={item.enShortName} placeholder="编码取值"
                        onChange={(event) => updateDetail(index, "enShortName", event.target.value)} />
                      <Input size="small" variant="outlined" value={item.cnName} placeholder="中文名称"
                        onChange={(event) => updateDetail(index, "cnName", event.target.value)} />
                      <Input size="small" variant="outlined" value={item.enName} placeholder="英文名称"
                        onChange={(event) => updateDetail(index, "enName", event.target.value)} />
                      <Input size="small" variant="outlined" value={item.remark} placeholder="编码含义"
                        onChange={(event) => updateDetail(index, "remark", event.target.value)} />
                      <Button size="sm" variant="ghost" isIconOnly aria-label="删除明细"
                        onClick={() => setDetails((current) => current.filter((_, itemIndex) => itemIndex !== index))}>
                        <Trash2 size={13} className="text-[#d92d20]" />
                      </Button>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="px-3 py-5 text-center text-xs text-[var(--yak-components-muted-text)]">暂无代码明细</div>
              )}
            </div>
          </>
        ) : null}

        {error ? <p role="alert" className="pl-[116px] text-xs text-[#d92d20]">{error}</p> : null}
      </div>
    </Modal>
  );
}

export default StandardRecordFormDialog;
