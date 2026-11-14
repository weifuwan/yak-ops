import { useEffect, useState } from "react";
import {
  Button, Field, FieldLabel, Input, Modal, Select, SelectContent, SelectItem,
  SelectItemText, SelectTrigger, SelectValue, Textarea,
} from "@yak-ops/yak-ui";

import type {
  DataMartOption, SubjectDomainFormValues, SubjectDomainRecord,
} from "./types";
import { withSelectedOption } from "../select-options";
import { EMPTY_SUBJECT_DOMAIN_FORM } from "./types";

interface Props {
  open: boolean;
  editing?: SubjectDomainRecord;
  parentId?: string;
  inheritedDataMartId?: string;
  inheritedDataMartName?: string;
  dataMarts: DataMartOption[];
  dataMartsLoading?: boolean;
  dataMartsError?: boolean;
  onRetryDataMarts?: () => void;
  saving: boolean;
  onClose: () => void;
  onSubmit: (values: SubjectDomainFormValues) => void;
}

const FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

export function SubjectDomainFormDialog({
  open, editing, parentId, inheritedDataMartId, inheritedDataMartName, dataMarts, dataMartsLoading = false, dataMartsError = false, onRetryDataMarts, saving, onClose, onSubmit,
}: Props) {
  const [values, setValues] = useState<SubjectDomainFormValues>(EMPTY_SUBJECT_DOMAIN_FORM);
  const [error, setError] = useState("");
  const isChild = Boolean(parentId || editing?.parentId);
  const lockedDataMartId = inheritedDataMartId || editing?.dataMartId || "";
  const availableDataMarts = dataMarts.filter((item) => Boolean(item.id));
  const dataMartOptions = availableDataMarts.map((item) => ({
    value: item.id || "",
    label: item.martName || "未命名数据集市",
  }));
  const selectedDataMartId = isChild ? lockedDataMartId : values.dataMartId;
  const dataMartSelectItems = withSelectedOption(
    dataMartOptions,
    selectedDataMartId,
    editing?.dataMartName || inheritedDataMartName || "已关联数据集市（名称暂不可用）",
  );

  useEffect(() => {
    if (!open) return;
    setValues(editing ? {
      enShortName: editing.enShortName || "",
      domainName: editing.domainName || "",
      director: editing.director || "",
      dataMartId: editing.dataMartId || "",
      remark: editing.remark || "",
    } : {
      ...EMPTY_SUBJECT_DOMAIN_FORM,
      dataMartId: inheritedDataMartId || "",
    });
    setError("");
  }, [open, editing, inheritedDataMartId]);

  const update = (key: keyof SubjectDomainFormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setError("");
  };

  const submit = () => {
    const enShortName = values.enShortName.trim();
    const domainName = values.domainName.trim();
    const director = values.director.trim();
    const dataMartId = isChild ? lockedDataMartId : values.dataMartId;
    if (!enShortName) return setError("请输入英文缩写");
    if (enShortName.length > 64) return setError("英文缩写不能超过 64 个字符");
    if (!domainName) return setError("请输入主题域名称");
    if (domainName.length > 64) return setError("主题域名称不能超过 64 个字符");
    if (!director) return setError("请输入负责人");
    if (director.length > 32) return setError("负责人不能超过 32 个字符");
    if (!dataMartId) return setError("请选择所属数据集市");
    if (values.remark.length > 2048) return setError("备注不能超过 2048 个字符");
    onSubmit({
      enShortName,
      domainName,
      director,
      dataMartId,
      remark: values.remark.trim(),
    });
  };

  return (
    <Modal
      open={open}
      centered
      width={640}
      title={editing ? "编辑主题域" : isChild ? "新建子主题域" : "新建主题域"}
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
          <FieldLabel required className="pt-1.5 text-xs">主题域名称</FieldLabel>
          <Input size="small" variant="outlined" maxLength={64} value={values.domainName}
            placeholder="请输入主题域名称" onChange={(event) => update("domainName", event.target.value)} />
        </Field>
        <Field className={FIELD_CLASS}>
          <FieldLabel required className="pt-1.5 text-xs">负责人</FieldLabel>
          <Input size="small" variant="outlined" maxLength={32} value={values.director}
            placeholder="请输入负责人" onChange={(event) => update("director", event.target.value)} />
        </Field>
        <Field className={FIELD_CLASS}>
          <FieldLabel required className="pt-1.5 text-xs">所属数据集市</FieldLabel>
          <Select
            items={dataMartSelectItems}
            value={(isChild ? lockedDataMartId : values.dataMartId) || undefined}
            disabled={isChild || (dataMartsLoading && availableDataMarts.length === 0) || (dataMartsError && availableDataMarts.length === 0)}
            onValueChange={(value) => update("dataMartId", String(value || ""))}
          >
            <SelectTrigger size="small" variant="outlined" className="w-full">
              <SelectValue placeholder="请选择数据集市" />
            </SelectTrigger>
            <SelectContent
              emptyContent={availableDataMarts.length === 0
                ? dataMartsError ? "数据集市加载失败，请重试" : dataMartsLoading ? "正在加载数据集市..." : "暂无数据集市"
                : undefined}
            >
              {dataMartOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  <SelectItemText>{option.label}</SelectItemText>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {!isChild && dataMartsError ? (
            <div className="mt-1 flex items-center justify-between gap-2 text-[11px] text-[#d92d20]">
              <span>{availableDataMarts.length ? "数据集市刷新失败，当前显示已加载的数据" : "数据集市加载失败，暂时无法选择"}</span>
              {onRetryDataMarts ? <Button size="sm" variant="ghost" onClick={onRetryDataMarts}>重试</Button> : null}
            </div>
          ) : null}
        </Field>
        <Field className={FIELD_CLASS}>
          <FieldLabel className="pt-1.5 text-xs">备注</FieldLabel>
          <Textarea size="small" variant="outlined" rows={3} maxLength={2048} value={values.remark}
            placeholder="请输入备注" onValueChange={(value) => update("remark", String(value || ""))} />
        </Field>
        {isChild ? (
          <p className="pl-[116px] text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
            子主题域将继承当前主题域所属的数据集市
          </p>
        ) : null}
        {error ? <p role="alert" className="pl-[116px] text-xs text-[#d92d20]">{error}</p> : null}
      </div>
    </Modal>
  );
}

export default SubjectDomainFormDialog;
