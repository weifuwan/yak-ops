import { useMemo } from "react";
import {
  Cascader,
  Combobox,
  ComboboxContent,
  ComboboxInput,
  ComboboxItem,
  ComboboxItemIndicator,
  Field,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
} from "@yak-ops/yak-ui";
import { X } from "lucide-react";

import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import { IndicatorSectionHeader } from "../../workbench/components/workspace/indicator-section-header";
import type { IndicatorEditorFieldsProps, IndicatorFieldOption, IndicatorOptionResource } from "../../workbench/types";
import { fieldOptions, selectOptionLabel } from "../../workbench/utils/indicator-form";
import { useDerivativeBusinessProcessCascader } from "../hooks/use-derivative-business-process-cascader";

type Props = IndicatorEditorFieldsProps;

const DW_TYPE_SELECT_ITEMS = {
  __NONE__: "不选择",
  PUBLIC_LAYER: "公共层",
  APPLICATION_LAYER: "应用层",
} as const;

function FormRow({
  label,
  required = false,
  children,
}: {
  label: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <Field className="grid grid-cols-1 items-start gap-2 sm:grid-cols-[116px_minmax(0,1fr)] sm:gap-3">
      <FieldLabel required={required} className="shrink-0 pt-1.5 text-[length:var(--yak-font-size-control-small)] font-normal leading-5 text-[var(--yak-components-muted-text)]">
        {label}
      </FieldLabel>
      <div className="min-w-0 max-w-[820px]">{children}</div>
    </Field>
  );
}

function resourceOptions(
  resource: IndicatorOptionResource,
  label: string,
  optionsByResource: Partial<Record<IndicatorOptionResource, ModelingRecord[]>>,
  optionLabelField?: string,
): IndicatorFieldOption[] {
  return fieldOptions({ key: resource, label, optionsSource: resource, optionLabelField }, optionsByResource);
}

function OptionSelect({
  value,
  options,
  placeholder,
  loading = false,
  disabled = false,
  onChange,
}: {
  value: string;
  options: IndicatorFieldOption[];
  placeholder: string;
  loading?: boolean;
  disabled?: boolean;
  onChange: (value: string) => void;
}) {
  return (
    <Select
      size="small"
      value={value || undefined}
      disabled={disabled}
      onValueChange={(next) => onChange(String(next || ""))}
    >
      <SelectTrigger variant="outlined" className="w-full">
        <SelectValue placeholder={loading ? "正在加载选项..." : placeholder}>
          {(selected) => selectOptionLabel(options, selected, loading ? "正在加载选项..." : placeholder)}
        </SelectValue>
      </SelectTrigger>
      <SelectContent emptyContent={loading ? "正在加载选项..." : options.length === 0 ? "暂无可选数据" : undefined}>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value} disabled={option.disabled}>
            <SelectItemText>{option.label}</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

export function DerivativeIndicatorEditorFields({
  projectCode,
  values,
  recordId,
  optionsByResource,
  optionsLoading,
  onChange,
  onChangeMany,
}: Props) {
  const timePeriodOptions = resourceOptions("timePeriod", "时间周期", optionsByResource);
  const atomOptions = resourceOptions("atomIndicator", "原子指标", optionsByResource);
  const modifierOptions = resourceOptions("modifier", "修饰词", optionsByResource);
  const businessTypeOptions = resourceOptions("businessType", "业务分类", optionsByResource, "businessName");
  const martOptions = resourceOptions("dataMart", "数据集市", optionsByResource, "martName");
  const subjectOptions = useMemo(
    () => resourceOptions("subjectDomain", "主题域", optionsByResource, "domainName")
      .filter((option) => (optionsByResource.subjectDomain || []).some((record) =>
        String(record.id || "") === option.value && String(record.dataMartId || "") === String(values.martId || ""),
      )),
    [optionsByResource, values.martId],
  );
  const processQuery = useDerivativeBusinessProcessCascader(projectCode, values.busTypeId);
  const modifierIds = (values.modifierId || "").split(/[,，]/).map((id) => id.trim()).filter(Boolean);
  const selectedModifierIds = Array.from(new Set(modifierIds));
  const supportedDwType = values.dwType === "PUBLIC_LAYER" || values.dwType === "APPLICATION_LAYER"
    ? values.dwType
    : "";

  const changeDwType = (next: string) => {
    onChangeMany({
      dwType: next === "__NONE__" ? "" : next,
      busTypeId: "",
      domainId: "",
      busProcessId: "",
      martId: "",
      subjectId: "",
    });
  };

  const updateModifierIds = (ids: string[]) => onChange("modifierId", Array.from(new Set(ids)).join(","));

  return (
    <div className="space-y-4">
      <section className="bg-white p-4">
        <div className="max-w-[1080px]">
          <IndicatorSectionHeader title="派生指标定义" />
          <div className="space-y-3">
            <FormRow label="时间周期" required>
              <OptionSelect
                value={values.timePeriodId || ""}
                options={timePeriodOptions}
                placeholder="请选择时间周期"
                loading={optionsLoading}
                onChange={(value) => onChange("timePeriodId", value)}
              />
            </FormRow>
            <FormRow label="修饰词">
              <div className="space-y-2">
                <Combobox<string, true>
                  multiple
                  size="small"
                  items={modifierOptions.map((option) => option.value)}
                  value={selectedModifierIds}
                  disabled={optionsLoading}
                  onValueChange={updateModifierIds}
                  onOpenChange={(nextOpen, eventDetails) => {
                    if (!nextOpen && eventDetails.reason === "item-press") eventDetails.cancel();
                  }}
                >
                  <ComboboxInput
                    variant="outlined"
                    placeholder={optionsLoading ? "正在加载修饰词..." : "搜索并选择修饰词（可多选）"}
                  />
                  <ComboboxContent emptyText={optionsLoading ? "正在加载修饰词..." : "暂无可选修饰词"}>
                    {(id: string) => (
                      <ComboboxItem key={id} value={id}>
                        <span className="min-w-0 flex-1 truncate">
                          {modifierOptions.find((option) => option.value === id)?.label || id}
                        </span>
                        <ComboboxItemIndicator />
                      </ComboboxItem>
                    )}
                  </ComboboxContent>
                </Combobox>
                {selectedModifierIds.length ? (
                  <div className="flex flex-wrap gap-1.5">
                    {selectedModifierIds.map((id) => (
                      <span key={id} className="inline-flex max-w-full items-center gap-1 rounded bg-[#f1f3f7] px-2 py-1 text-[11px] text-[#4b5563]">
                        <span className="max-w-[240px] truncate">{modifierOptions.find((option) => option.value === id)?.label || id}</span>
                        <button
                          type="button"
                          aria-label={"移除修饰词 " + (modifierOptions.find((option) => option.value === id)?.label || id)}
                          className="inline-flex size-3.5 shrink-0 items-center justify-center rounded hover:bg-black/10"
                          onClick={() => updateModifierIds(selectedModifierIds.filter((current) => current !== id))}
                        >
                          <X size={10} />
                        </button>
                      </span>
                    ))}
                  </div>
                ) : null}
              </div>
            </FormRow>
            <FormRow label="原子指标" required>
              <OptionSelect
                value={values.atomicId || ""}
                options={atomOptions}
                placeholder="请选择原子指标"
                loading={optionsLoading}
                onChange={(value) => onChange("atomicId", value)}
              />
            </FormRow>
          </div>
        </div>
      </section>

      <section className="bg-white p-4">
        <div className="max-w-[1080px]">
          <IndicatorSectionHeader title="基本信息" />
          <div className="space-y-3">
            <FormRow label="英文缩写" required>
              <Input size="small" variant="outlined" maxLength={128} value={values.enShortName || ""} disabled={Boolean(recordId)} placeholder="请输入英文缩写" onChange={(event) => onChange("enShortName", event.target.value)} />
            </FormRow>
            <FormRow label="中文名称" required>
              <Input size="small" variant="outlined" maxLength={2048} value={values.cnName || ""} placeholder="请输入中文名称" onChange={(event) => onChange("cnName", event.target.value)} />
            </FormRow>
            <FormRow label="英文名称">
              <Input size="small" variant="outlined" maxLength={2048} value={values.enName || ""} placeholder="请输入英文名称" onChange={(event) => onChange("enName", event.target.value)} />
            </FormRow>
            <FormRow label="负责人">
              <Input size="small" variant="outlined" maxLength={32} value={values.director || ""} placeholder="请输入负责人" onChange={(event) => onChange("director", event.target.value)} />
            </FormRow>
            <FormRow label="描述">
              <Textarea size="small" variant="outlined" rows={3} maxLength={2048} value={values.remark || ""} placeholder="请输入描述" onValueChange={(value) => onChange("remark", String(value || ""))} />
            </FormRow>
          </div>
        </div>
      </section>

      <section className="bg-white p-4">
        <div className="max-w-[1080px]">
          <IndicatorSectionHeader title="数仓归属" />
          <div className="space-y-3">
            <FormRow label="数仓分层">
              <Select<string> size="small" items={DW_TYPE_SELECT_ITEMS} value={supportedDwType || "__NONE__"} onValueChange={(value) => changeDwType(String(value || "__NONE__"))}>
                <SelectTrigger variant="outlined" className="w-full">
                  <SelectValue placeholder="请选择数仓分层" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__NONE__"><SelectItemText>不选择</SelectItemText><SelectItemIndicator /></SelectItem>
                  <SelectItem value="PUBLIC_LAYER"><SelectItemText>公共层</SelectItemText><SelectItemIndicator /></SelectItem>
                  <SelectItem value="APPLICATION_LAYER"><SelectItemText>应用层</SelectItemText><SelectItemIndicator /></SelectItem>
                </SelectContent>
              </Select>
            </FormRow>

            {!supportedDwType ? (
              <p className="rounded bg-[var(--yak-color-hover)] px-3 py-2 text-[11px] text-[var(--yak-components-muted-text)]">
                未选择数仓分层，当前派生指标不设置业务归属。
              </p>
            ) : null}

            {supportedDwType === "PUBLIC_LAYER" ? (
              <>
                <FormRow label="业务分类">
                  <OptionSelect
                    value={values.busTypeId || ""}
                    options={businessTypeOptions}
                    placeholder="请选择业务分类（非必填）"
                    loading={optionsLoading}
                    onChange={(value) => onChangeMany({ busTypeId: value, domainId: "", busProcessId: "" })}
                  />
                </FormRow>
                <FormRow label="业务过程" required>
                  <div>
                    <Cascader
                      items={processQuery.options}
                      value={values.domainId && values.busProcessId ? [values.domainId, values.busProcessId] : null}
                      columnLabels={["数据域", "业务过程"]}
                      placeholder={processQuery.isFetching ? "正在加载业务过程..." : "请选择数据域及业务过程"}
                      emptyText="暂无可选业务过程"
                      disabled={processQuery.isFetching}
                      size="small"
                      variant="outlined"
                      onValueChange={(path) => onChangeMany({
                        domainId: path?.[0] || "",
                        busProcessId: path?.[path.length - 1] || "",
                      })}
                    />
                    {processQuery.isError ? (
                      <p className="mt-1 text-[11px] text-[var(--yak-components-danger-text)]">业务过程加载失败，请刷新后重试。</p>
                    ) : null}
                  </div>
                </FormRow>
              </>
            ) : null}

            {supportedDwType === "APPLICATION_LAYER" ? (
              <>
                <FormRow label="数据集市" required>
                  <OptionSelect
                    value={values.martId || ""}
                    options={martOptions}
                    placeholder="请选择数据集市"
                    loading={optionsLoading}
                    onChange={(value) => onChangeMany({ martId: value, subjectId: "" })}
                  />
                </FormRow>
                <FormRow label="主题域" required>
                  <OptionSelect
                    value={values.subjectId || ""}
                    options={subjectOptions}
                    placeholder={values.martId ? "请选择主题域" : "请先选择数据集市"}
                    loading={optionsLoading}
                    disabled={!values.martId}
                    onChange={(value) => onChange("subjectId", value)}
                  />
                </FormRow>
              </>
            ) : null}
          </div>
        </div>
      </section>
    </div>
  );
}
