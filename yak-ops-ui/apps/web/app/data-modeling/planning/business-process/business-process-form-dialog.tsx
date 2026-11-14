import { useEffect, useState } from "react";
import {
  Button, Input, Modal, Select, SelectContent, SelectItem, SelectItemText,
  SelectTrigger, SelectValue, Textarea,
} from "@yak-ops/yak-ui";

import type { DataDomainRecord } from "../data-domain/types";
import { withSelectedOption } from "../select-options";
import { isSystemBusinessProcess } from "../data-domain/types";
import type { BusinessProcessRecord } from "./types";
import { EMPTY_FORM, type BusinessProcessFormValues } from "./types";

interface Props {
  open: boolean;
  editing?: BusinessProcessRecord;
  domains: DataDomainRecord[];
  domainsLoading?: boolean;
  domainsError?: boolean;
  onRetryDomains?: () => void;
  saving: boolean;
  onClose: () => void;
  onSubmit: (values: BusinessProcessFormValues) => void;
}

const FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3";
const LABEL_CLASS = "pt-1.5 text-right text-xs text-[var(--yak-components-muted-text)]";

export function BusinessProcessFormDialog({
  open, editing, domains, domainsLoading = false, domainsError = false, onRetryDomains, saving, onClose, onSubmit,
}: Props) {
  const [values, setValues] = useState<BusinessProcessFormValues>(EMPTY_FORM);
  const [error, setError] = useState("");
  const system = isSystemBusinessProcess(editing);
  const availableDomains = domains.filter((domain) => Boolean(domain.id));
  const domainOptions = availableDomains.map((domain) => ({
    value: domain.id || "",
    label: `${domain.cnName || "未命名数据域"}（${domain.enShortName || "-"}）`,
  }));
  const domainSelectItems = withSelectedOption(
    domainOptions,
    values.dataDomainId,
    editing?.dataDomainCnName || "已关联数据域（名称暂不可用）",
  );

  useEffect(() => {
    if (!open) return;
    setValues(editing ? {
      enShortName: editing.enShortName || "",
      enName: editing.enName || "",
      cnName: editing.cnName || "",
      dataDomainId: editing.dataDomainId || "",
      director: editing.director || "",
      remark: editing.remark || "",
    } : EMPTY_FORM);
    setError("");
  }, [open, editing]);

  const update = (key: keyof BusinessProcessFormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    if (error) setError("");
  };

  const submit = () => {
    const enShortName = values.enShortName.trim();
    const enName = values.enName.trim();
    const cnName = values.cnName.trim();
    const dataDomainId = values.dataDomainId.trim();
    const director = values.director.trim();

    if (!enShortName) return setError("请输入英文缩写");
    if (enShortName.length > 64 || !/^[a-z0-9](?:[a-z0-9_]*[a-z0-9])?$/.test(enShortName)) {
      return setError("英文缩写请使用小写字母、数字和下划线，且不能以下划线结尾");
    }
    if (!enName) return setError("请输入英文名");
    if (enName.length > 64 || !/^[a-zA-Z0-9](?:[_&a-zA-Z0-9\s]*[_&a-zA-Z0-9])?$/.test(enName)) {
      return setError("英文名请以字母或数字开头，支持下划线和 &");
    }
    if (!cnName) return setError("请输入中文名");
    if (cnName.length > 64 || !/^[a-zA-Z0-9\u4e00-\u9fa5](?:[_&a-zA-Z0-9\u4e00-\u9fa5]*)$/.test(cnName)) {
      return setError("中文名格式不正确，请检查特殊字符");
    }
    if (!dataDomainId) return setError("请选择所属数据域");
    if (!director) return setError("请输入负责人");
    if (director.length > 32) return setError("负责人不能超过 32 个字符");
    if (values.remark.length > 2048) return setError("备注不能超过 2048 个字符");

    onSubmit({
      enShortName,
      enName,
      cnName,
      dataDomainId,
      director,
      remark: values.remark.trim(),
    });
  };

  return (
    <Modal
      open={open}
      centered
      width={630}
      title={editing ? "编辑业务过程" : "新增业务过程"}
      onClose={onClose}
      footer={<>
        <Button size="sm" variant="secondary" isDisabled={saving} onClick={onClose}>取消</Button>
        <Button size="sm" variant="primary" isPending={saving} onClick={submit}>确定</Button>
      </>}
    >
      <div className="space-y-3">
        <label className={FIELD_CLASS}>
          <span className={LABEL_CLASS}><span className="mr-1 text-[#d92d20]">*</span>英文缩写</span>
          <Input size="small" variant="outlined" maxLength={64} value={values.enShortName}
            disabled={system} placeholder="请输入英文缩写"
            onChange={(event) => update("enShortName", event.target.value)} />
        </label>
        <label className={FIELD_CLASS}>
          <span className={LABEL_CLASS}><span className="mr-1 text-[#d92d20]">*</span>英文名</span>
          <Input size="small" variant="outlined" maxLength={64} value={values.enName}
            disabled={system} placeholder="请输入英文名"
            onChange={(event) => update("enName", event.target.value)} />
        </label>
        <label className={FIELD_CLASS}>
          <span className={LABEL_CLASS}><span className="mr-1 text-[#d92d20]">*</span>中文名</span>
          <Input size="small" variant="outlined" maxLength={64} value={values.cnName}
            disabled={system} placeholder="请输入中文名"
            onChange={(event) => update("cnName", event.target.value)} />
        </label>
        <div className={FIELD_CLASS}>
          <span className={LABEL_CLASS}><span className="mr-1 text-[#d92d20]">*</span>数据域</span>
          <Select
            items={domainSelectItems}
            value={values.dataDomainId || undefined}
            disabled={system || (domainsLoading && availableDomains.length === 0) || (domainsError && availableDomains.length === 0)}
            onValueChange={(value) => update("dataDomainId", String(value || ""))}
          >
            <SelectTrigger size="small" variant="outlined" className="w-full">
              <SelectValue placeholder="请选择数据域" />
            </SelectTrigger>
            <SelectContent
              emptyContent={availableDomains.length === 0
                ? domainsError ? "数据域加载失败，请重试" : domainsLoading ? "正在加载数据域..." : "暂无数据域，请先创建"
                : undefined}
            >
              {domainOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  <SelectItemText>{option.label}</SelectItemText>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {domainsError ? (
            <div className="mt-1 flex items-center justify-between gap-2 text-[11px] text-[#d92d20]">
              <span>{availableDomains.length ? "数据域刷新失败，当前显示已加载的数据" : "数据域加载失败，暂时无法选择"}</span>
              {onRetryDomains ? <Button size="sm" variant="ghost" onClick={onRetryDomains}>重试</Button> : null}
            </div>
          ) : null}
        </div>
        <label className={FIELD_CLASS}>
          <span className={LABEL_CLASS}><span className="mr-1 text-[#d92d20]">*</span>负责人</span>
          <Input size="small" variant="outlined" maxLength={32} value={values.director}
            placeholder="请输入负责人"
            onChange={(event) => update("director", event.target.value)} />
        </label>
        <label className={FIELD_CLASS}>
          <span className={LABEL_CLASS}>备注</span>
          <Textarea size="small" variant="outlined" rows={3} maxLength={2048}
            value={values.remark} disabled={system} placeholder="请输入备注"
            onValueChange={(value) => update("remark", String(value || ""))} />
        </label>
        {system ? (
          <p className="pl-[116px] text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
            系统默认业务过程的英文缩写、英文名、中文名、所属数据域和备注不可修改
          </p>
        ) : null}
        {error ? <p role="alert" className="pl-[116px] text-xs text-[#d92d20]">{error}</p> : null}
      </div>
    </Modal>
  );
}

export default BusinessProcessFormDialog;
