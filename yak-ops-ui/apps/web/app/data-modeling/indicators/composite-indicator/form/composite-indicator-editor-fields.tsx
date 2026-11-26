import { useEffect, useMemo } from "react";
import {
  Cascader,
  Field,
  FieldLabel,
  Input,
  Radio,
  RadioGroup,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
} from "@yak-ops/yak-ui";

import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import { IndicatorSectionHeader } from "../../workbench/components/workspace/indicator-section-header";
import type { IndicatorEditorFieldsProps, IndicatorFieldOption, IndicatorOptionResource } from "../../workbench/types";
import { fieldOptions, selectOptionLabel } from "../../workbench/utils/indicator-form";
import {
  COMPOSITE_COMPARE_TYPE_OPTIONS,
  COMPOSITE_COMPUTE_MODE_OPTIONS,
  COMPOSITE_DW_TYPE_OPTIONS,
  COMPOSITE_RATIO_FORMULA,
} from "../config";
import { useDerivativeBusinessProcessCascader } from "../../derivative-indicator/hooks/use-derivative-business-process-cascader";

type Props = IndicatorEditorFieldsProps;

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

function resourceOptions(
  resource: IndicatorOptionResource,
  label: string,
  optionsByResource: Partial<Record<IndicatorOptionResource, ModelingRecord[]>>,
  optionLabelField?: string,
): IndicatorFieldOption[] {
  return fieldOptions({ key: resource, label, optionsSource: resource, optionLabelField }, optionsByResource);
}

export function CompositeIndicatorEditorFields({
  projectCode,
  values,
  recordId,
  optionsByResource,
  optionsLoading,
  onChange,
  onChangeMany,
}: Props) {
  const selectedDwType = String(values.dwType || "");
  const businessTypeOptions = resourceOptions("businessType", "业务分类", optionsByResource, "businessName");
  const martOptions = resourceOptions("dataMart", "数据集市", optionsByResource, "martName");
  const subjectOptions = useMemo(
    () => resourceOptions("subjectDomain", "主题域", optionsByResource, "domainName")
      .filter((option) => (optionsByResource.subjectDomain || []).some((record) =>
        String(record.id || "") === option.value && String(record.dataMartId || "") === String(values.martId || ""),
      )),
    [optionsByResource, values.martId],
  );
  const deriveOptions = useMemo(
    () => (optionsByResource.derivativeIndicator || [])
      .filter((record) => Boolean(record.id))
      .map((record) => {
        const shortName = String(record.enShortName || "");
        const name = String(record.cnName || record.id || "");
        return {
          value: String(record.id),
          label: shortName ? shortName + (name ? " (" + name + ")" : "") : name,
        };
      }),
    [optionsByResource.derivativeIndicator],
  );
  const processQuery = useDerivativeBusinessProcessCascader(projectCode, values.busTypeId);

  useEffect(() => {
    if (
      selectedDwType !== "PUBLIC_LAYER"
      || !values.busProcessId
      || processQuery.isFetching
      || processQuery.isError
    ) {
      return;
    }
    const processStillAvailable = processQuery.options.some((domain) =>
      domain.value === values.domainId
      && (domain.children || []).some((process) => process.value === values.busProcessId),
    );
    if (!processStillAvailable) {
      onChangeMany({ domainId: "", busProcessId: "" });
    }
  }, [
    selectedDwType,
    values.busTypeId,
    values.domainId,
    values.busProcessId,
    processQuery.isFetching,
    processQuery.isError,
    processQuery.options,
    onChangeMany,
  ]);

  const computeMode = values.computeMode === "1" ? "1" : "0";

  const changeComputeMode = (next: string) => {
    onChangeMany(next === "1"
      ? { computeMode: "1", computeExpr: "" }
      : { computeMode: "0", deriveId: "", compareType: "" });
  };

  const changeWarehouseType = (next: string) => {
    onChangeMany({
      dwType: next,
      busTypeId: "",
      domainId: "",
      busProcessId: "",
      martId: "",
      subjectId: "",
    });
  };

  return (
    <div className="space-y-4">
      <section className="bg-white p-4">
        <div className="max-w-[1080px]">
          <IndicatorSectionHeader title="计算逻辑" />
          <div className="space-y-3">
            <FormRow label="计算模式" required>
              <RadioGroup
                aria-label="计算模式"
                value={computeMode}
                onValueChange={(value) => changeComputeMode(String(value))}
                className="flex flex-wrap items-center gap-5 pt-1"
              >
                {COMPOSITE_COMPUTE_MODE_OPTIONS.map((option) => (
                  <label key={option.value} className="inline-flex cursor-pointer items-center gap-2 text-xs text-[var(--yak-components-panel-text)]">
                    <Radio value={option.value} />
                    <span>{option.label}</span>
                  </label>
                ))}
              </RadioGroup>
            </FormRow>

            {computeMode === "1" ? (
              <>
                <FormRow label="派生指标" required>
                  <OptionSelect
                    value={values.deriveId || ""}
                    options={deriveOptions}
                    placeholder="请选择派生指标"
                    loading={optionsLoading}
                    onChange={(value) => onChange("deriveId", value)}
                  />
                </FormRow>
                <FormRow label="对比类型" required>
                  <OptionSelect
                    value={values.compareType || ""}
                    options={COMPOSITE_COMPARE_TYPE_OPTIONS}
                    placeholder="请选择对比类型"
                    onChange={(value) => onChange("compareType", value)}
                  />
                </FormRow>
                <FormRow label="计算口径">
                  <p className="py-1 text-xs leading-5 text-[var(--yak-components-panel-text)]">{COMPOSITE_RATIO_FORMULA}</p>
                </FormRow>
              </>
            ) : (
              <FormRow label="计算口径" required>
                <div>
                  <Textarea
                    size="small"
                    variant="outlined"
                    rows={3}
                    maxLength={2048}
                    value={values.computeExpr || ""}
                    placeholder="请输入计算表达式"
                    onValueChange={(value) => onChange("computeExpr", String(value || ""))}
                  />
                  <p className="mt-1 text-[11px] text-[var(--yak-components-muted-text)]">输入 @ 可以引用其他派生指标或复合指标。</p>
                </div>
              </FormRow>
            )}
          </div>
        </div>
      </section>

      <section className="bg-white p-4">
        <div className="max-w-[1080px]">
          <IndicatorSectionHeader title="基本信息" />
          <div className="space-y-3">
            <FormRow label="数仓分类" required>
              <OptionSelect
                value={values.dwType || ""}
                options={COMPOSITE_DW_TYPE_OPTIONS}
                placeholder="请选择数仓分类"
                onChange={changeWarehouseType}
              />
            </FormRow>

            {selectedDwType === "PUBLIC_LAYER" ? (
              <>
                <FormRow label="业务分类">
                  <OptionSelect
                    value={values.busTypeId || ""}
                    options={businessTypeOptions}
                    placeholder="请选择业务分类（非必填）"
                    loading={optionsLoading}
                    onChange={(value) => onChange("busTypeId", value)}
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

            {selectedDwType === "APPLICATION_LAYER" ? (
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

            {!selectedDwType ? (
              <p className="rounded bg-[var(--yak-color-hover)] px-3 py-2 text-[11px] text-[var(--yak-components-muted-text)]">
                请选择数仓分类，再配置对应的业务归属。
              </p>
            ) : null}

            <FormRow label="英文缩写" required>
              <Input
                size="small"
                variant="outlined"
                maxLength={128}
                value={values.enShortName || ""}
                disabled={Boolean(recordId)}
                placeholder="请输入英文缩写"
                onChange={(event) => onChange("enShortName", event.target.value)}
              />
            </FormRow>
            <FormRow label="中文名称" required>
              <Input
                size="small"
                variant="outlined"
                maxLength={2048}
                value={values.cnName || ""}
                placeholder="请输入中文名称"
                onChange={(event) => onChange("cnName", event.target.value)}
              />
            </FormRow>
            <FormRow label="英文名称">
              <Input
                size="small"
                variant="outlined"
                maxLength={2048}
                value={values.enName || ""}
                placeholder="请输入英文名称"
                onChange={(event) => onChange("enName", event.target.value)}
              />
            </FormRow>
            <FormRow label="负责人" required>
              <Input
                size="small"
                variant="outlined"
                maxLength={32}
                value={values.director || ""}
                placeholder="请输入负责人"
                onChange={(event) => onChange("director", event.target.value)}
              />
            </FormRow>
            <FormRow label="描述">
              <Textarea
                size="small"
                variant="outlined"
                rows={3}
                maxLength={2048}
                value={values.remark || ""}
                placeholder="请输入描述"
                onValueChange={(value) => onChange("remark", String(value || ""))}
              />
            </FormRow>
          </div>
        </div>
      </section>
    </div>
  );
}
