import { useEffect, useState } from "react";
import {
  Button,
  Field,
  FieldLabel,
  Input,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
} from "@yak-ops/yak-ui";

import {
  DW_LAYER_TYPES,
  getDwModelTypeOptions,
  toDwLayerFormValues,
  type DwLayerFormValues,
  type DwLayerRecord,
} from "./constants";

interface DwLayerFormDialogProps {
  open: boolean;
  editing?: DwLayerRecord;
  saving: boolean;
  onClose: () => void;
  onSubmit: (values: DwLayerFormValues) => void;
}

const EMPTY_VALUES: DwLayerFormValues = {
  enShortName: "",
  enName: "",
  cnName: "",
  dwType: "",
  dwModelType: "",
  director: "",
  remark: "",
};

export function DwLayerFormDialog({
  open,
  editing,
  saving,
  onClose,
  onSubmit,
}: DwLayerFormDialogProps) {
  const [values, setValues] = useState<DwLayerFormValues>(EMPTY_VALUES);
  const [formError, setFormError] = useState("");

  useEffect(() => {
    if (!open) return;
    setValues(editing ? toDwLayerFormValues(editing) : EMPTY_VALUES);
    setFormError("");
  }, [open, editing]);

  const modelOptions = getDwModelTypeOptions(values.dwType);
  const setValue = (key: keyof DwLayerFormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setFormError("");
  };

  const handleSubmit = () => {
    const shortName = values.enShortName.trim();
    const englishName = values.enName.trim();
    const chineseName = values.cnName.trim();

    if (!shortName) {
      setFormError("请填写英文缩写");
      return;
    }
    if (shortName.length > 64 || !/^[A-Z0-9][A-Z0-9_]*$/.test(shortName)) {
      setFormError("英文缩写请使用大写字母、数字和下划线，且不能以下划线开头或结尾");
      return;
    }
    if (!englishName) {
      setFormError("请填写英文名称");
      return;
    }
    if (englishName.length > 64 || !/^[A-Za-z0-9](?:[A-Za-z0-9_&\s]*[A-Za-z0-9_&])?$/.test(englishName)) {
      setFormError("英文名称请以字母或数字开头，可使用字母、数字、空格、下划线和 &");
      return;
    }
    if (!chineseName) {
      setFormError("请填写中文名称");
      return;
    }
    if (chineseName.length > 64 || !/^[A-Za-z0-9\u4e00-\u9fa5][A-Za-z0-9_\u4e00-\u9fa5&]*$/.test(chineseName)) {
      setFormError("中文名称格式不正确，请检查特殊字符");
      return;
    }
    if (!values.director.trim()) {
      setFormError("请填写负责人");
      return;
    }
    if (values.director.trim().length > 32) {
      setFormError("负责人最多输入 32 个字符");
      return;
    }
    if (!values.dwType) {
      setFormError("请选择分层归属");
      return;
    }
    if (values.dwType !== "NONE_LAYER" && !values.dwModelType) {
      setFormError("请选择模型类型");
      return;
    }
    if (values.remark.length > 2048) {
      setFormError("备注最多输入 2048 个字符");
      return;
    }

    onSubmit({
      enShortName: shortName,
      enName: englishName,
      cnName: chineseName,
      dwType: values.dwType,
      dwModelType: values.dwModelType,
      director: values.director.trim(),
      remark: values.remark.trim(),
    });
  };

  return (
    <Modal
      open={open}
      centered
      width={630}
      title={editing ? "编辑数仓分层" : "新建数仓分层"}
      onClose={onClose}
      footer={
        <>
          <Button size="sm" variant="secondary" isDisabled={saving} onClick={onClose}>
            取消
          </Button>
          <Button size="sm" variant="primary" isPending={saving} onClick={handleSubmit}>
            保存
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required className="pt-1.5 text-xs">英文缩写</FieldLabel>
          <Input
            size="small"
            variant="outlined"
            maxLength={64}
            value={values.enShortName}
            placeholder="例如：ODS_ORDER"
            onChange={(event) => setValue("enShortName", event.target.value.toUpperCase())}
          />
        </Field>

        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required className="pt-1.5 text-xs">英文名称</FieldLabel>
          <Input
            size="small"
            variant="outlined"
            maxLength={64}
            value={values.enName}
            placeholder="请输入英文名称"
            onChange={(event) => setValue("enName", event.target.value)}
          />
        </Field>

        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required className="pt-1.5 text-xs">中文名称</FieldLabel>
          <Input
            size="small"
            variant="outlined"
            maxLength={64}
            value={values.cnName}
            placeholder="请输入中文名称"
            onChange={(event) => setValue("cnName", event.target.value)}
          />
        </Field>

        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required className="pt-1.5 text-xs">负责人</FieldLabel>
          <Input
            size="small"
            variant="outlined"
            maxLength={32}
            value={values.director}
            placeholder="请输入负责人"
            onChange={(event) => setValue("director", event.target.value)}
          />
        </Field>

        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required className="pt-1.5 text-xs">分层归属</FieldLabel>
          <div className="min-w-0">
            <Select
              size="small"
              items={DW_LAYER_TYPES}
              value={values.dwType || undefined}
              disabled={Boolean(editing)}
              onValueChange={(value) => {
                setValues((current) => ({
                  ...current,
                  dwType: String(value || ""),
                  dwModelType: "",
                }));
                setFormError("");
              }}
            >
              <SelectTrigger variant="outlined">
                <SelectValue placeholder="请选择分层归属" />
              </SelectTrigger>
              <SelectContent>
                {DW_LAYER_TYPES.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    <SelectItemText>{option.label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </Field>

        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required={values.dwType !== "NONE_LAYER"} className="pt-1.5 text-xs">
            模型类型
          </FieldLabel>
          <div className="min-w-0">
            <Select
              size="small"
              items={modelOptions}
              value={values.dwModelType || undefined}
              disabled={Boolean(editing) || !values.dwType || modelOptions.length === 0}
              onValueChange={(value) => setValue("dwModelType", String(value || ""))}
            >
              <SelectTrigger variant="outlined">
                <SelectValue
                  placeholder={
                    values.dwType === "NONE_LAYER"
                      ? "未分层无需指定模型类型"
                      : values.dwType
                        ? "请选择模型类型"
                        : "请先选择分层归属"
                  }
                />
              </SelectTrigger>
              <SelectContent>
                {modelOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    <SelectItemText>{option.label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </Field>

        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel className="pt-1.5 text-xs">备注</FieldLabel>
          <Textarea
            size="small"
            variant="outlined"
            rows={3}
            maxLength={2048}
            value={values.remark}
            placeholder="请输入备注"
            onValueChange={(value) => setValue("remark", String(value || ""))}
          />
        </Field>

        {formError ? (
          <p role="alert" className="pl-[116px] text-xs text-[#d92d20]">{formError}</p>
        ) : null}
      </div>
    </Modal>
  );
}

export default DwLayerFormDialog;
