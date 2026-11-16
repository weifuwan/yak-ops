import { useState } from "react";
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
  SelectSearch,
  SelectTrigger,
  SelectValue,
  Textarea,
  TreeSelect,
} from "@yak-ops/yak-ui";

import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import { IndicatorSectionHeader } from "../../workbench/components/workspace/indicator-section-header";
import type { IndicatorEditorFieldsProps, IndicatorFieldOption, IndicatorOptionResource } from "../../workbench/types";
import { CALCULATE_FUNCTION_OPTIONS } from "../config";
import { useAtomIndicatorOwnerOptions } from "../hooks/use-atom-indicator-owner-options";
import { useBusinessProcessCascader } from "../hooks/use-business-process-cascader";
import { useMeasurementUnitTree } from "../hooks/use-measurement-unit-tree";
import { fieldOptions, selectOptionLabel } from "../../workbench/utils/indicator-form";

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
  disabled = false,
  loading = false,
  searchable = false,
  onChange,
}: {
  value: string;
  options: IndicatorFieldOption[];
  placeholder: string;
  disabled?: boolean;
  loading?: boolean;
  searchable?: boolean;
  onChange: (value: string) => void;
}) {
  const [keyword, setKeyword] = useState("");
  const filteredOptions = searchable
    ? options.filter((option) => option.label.toLocaleLowerCase().includes(keyword.trim().toLocaleLowerCase()))
    : options;
  const emptyContent = loading
    ? "正在加载选项..."
    : filteredOptions.length === 0
      ? keyword.trim() ? "没有匹配的选项" : "暂无可选数据"
      : undefined;

  return (
    <Select
      size="small"
      value={value || undefined}
      disabled={disabled}
      onValueChange={(next) => {
        setKeyword("");
        onChange(String(next || ""));
      }}
    >
      <SelectTrigger variant="outlined" className="w-full">
        <SelectValue placeholder={loading ? "正在加载选项..." : placeholder}>
          {(selected) => selectOptionLabel(options, selected, loading ? "正在加载选项..." : placeholder)}
        </SelectValue>
      </SelectTrigger>
      <SelectContent
        header={searchable ? (
          <SelectSearch
            size="small"
            value={keyword}
            placeholder="搜索负责人"
            onChange={(event) => setKeyword(event.target.value)}
          />
        ) : undefined}
        emptyContent={emptyContent}
      >
        {filteredOptions.map((option) => (
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
  return fieldOptions(
    { key: resource, label, optionsSource: resource, optionLabelField },
    optionsByResource,
  );
}

export function AtomIndicatorEditorFields({
  projectCode,
  values,
  recordId,
  optionsByResource,
  optionsLoading,
  onChange,
  onChangeMany,
}: Props) {
  const businessTypeOptions = resourceOptions("businessType", "业务分类", optionsByResource, "businessName");
  const processQuery = useBusinessProcessCascader(projectCode, values.busTypeId);
  const unitTreeQuery = useMeasurementUnitTree(projectCode);
  const ownerQuery = useAtomIndicatorOwnerOptions(projectCode);
  const ownerOptions: IndicatorFieldOption[] = (ownerQuery.data || []).map((user) => ({
    value: user.userName,
    label: user.userName,
  }));

  if (values.director && !ownerOptions.some((option) => option.value === values.director)) {
    ownerOptions.unshift({ value: values.director, label: values.director });
  }

  return (
    <div className="space-y-4">
      <section className="bg-white p-4">
        <div className="max-w-[1080px]">
          <IndicatorSectionHeader title="原子指标基本信息" />
          <div className="space-y-3">
            <FormRow label="英文缩写" required>
              <Input
                size="small"
                variant="outlined"
                value={values.enShortName || ""}
                disabled={Boolean(recordId)}
                maxLength={500}
                placeholder="请输入英文缩写"
                onChange={(event) => onChange("enShortName", event.target.value)}
              />
              <div className="mt-1.5 flex items-center gap-1.5 rounded bg-[#fff7e8] px-2 py-1 text-[11px] leading-4 text-[#9a6b22]">
                <span aria-hidden="true">⚠</span>
                英文缩写是指标的唯一性标识，一经保存，无法二次修改。
              </div>
            </FormRow>
            <FormRow label="英文名称">
              <Input size="small" variant="outlined" maxLength={128} value={values.enName || ""} placeholder="请输入英文名称" onChange={(event) => onChange("enName", event.target.value)} />
            </FormRow>
            <FormRow label="中文名称" required>
              <Input size="small" variant="outlined" maxLength={2048} value={values.cnName || ""} placeholder="请输入中文名称" onChange={(event) => onChange("cnName", event.target.value)} />
            </FormRow>
            <FormRow label="业务口径" required>
              <Textarea size="small" variant="outlined" rows={3} maxLength={500} value={values.businessCaliber || ""} placeholder="请输入业务口径" onValueChange={(value) => onChange("businessCaliber", String(value || ""))} />
            </FormRow>
            <FormRow label="业务分类">
              <OptionSelect
                value={values.busTypeId || ""}
                options={businessTypeOptions}
                placeholder="请选择业务分类"
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
                ) : (
                  
                )}
              </div>
            </FormRow>
            <FormRow label="负责人" required>
              {ownerQuery.isFetching || ownerOptions.length > 0 ? (
                <OptionSelect
                  value={values.director || ""}
                  options={ownerOptions}
                  placeholder="请选择负责人"
                  loading={ownerQuery.isFetching}
                  searchable
                  onChange={(value) => onChange("director", value)}
                />
              ) : (
                <Input
                  size="small"
                  variant="outlined"
                  maxLength={32}
                  value={values.director || ""}
                  placeholder="请输入负责人"
                  onChange={(event) => onChange("director", event.target.value)}
                />
              )}
              {!ownerQuery.isFetching && ownerOptions.length === 0 ? (
                <p className="mt-1 text-[11px] text-[var(--yak-components-muted-text)]">当前账号无法获取用户列表，可手动填写负责人账号。</p>
              ) : null}
            </FormRow>
            <FormRow label="描述">
              <Textarea size="small" variant="outlined" rows={3} maxLength={2048} value={values.remark || ""} placeholder="请输入描述" onValueChange={(value) => onChange("remark", String(value || ""))} />
            </FormRow>
          </div>
        </div>
      </section>

      <section className="bg-white p-4">
        <div className="max-w-[1080px]">
          <IndicatorSectionHeader title="计算逻辑" />
          <div className="space-y-3">
            <FormRow label="计算函数">
              <OptionSelect value={values.calculateFunction || ""} options={CALCULATE_FUNCTION_OPTIONS} placeholder="请选择计算函数" onChange={(value) => onChange("calculateFunction", value)} />
            </FormRow>
            <FormRow label="小数位数">
              <Input
                size="small"
                variant="outlined"
                type="number"
                min={0}
                step={1}
                value={values.decimalNumber ?? "0"}
                placeholder="请输入小数位数"
                onChange={(event) => onChange("decimalNumber", event.target.value)}
              />
            </FormRow>
            <FormRow label="度量单位">
              <div>
                <TreeSelect
                  items={unitTreeQuery.options}
                  value={values.unitId || null}
                  placeholder={unitTreeQuery.isFetching ? "正在加载度量单位..." : "请选择度量单位"}
                  searchPlaceholder="搜索度量单位"
                  emptyText="暂无度量单位，请先维护数据标准"
                  disabled={unitTreeQuery.isFetching}
                  size="small"
                  variant="outlined"
                  onValueChange={(value) => onChange("unitId", value || "")}
                />
                {unitTreeQuery.isError ? (
                  <p className="mt-1 text-[11px] text-[var(--yak-components-danger-text)]">度量单位加载失败，请刷新后重试。</p>
                ) : null}
              </div>
            </FormRow>
            <FormRow label="是否去重">
              <RadioGroup
                aria-label="是否去重"
                value={values.removeDuplicates || "true"}
                onValueChange={(value) => onChange("removeDuplicates", String(value))}
                className="flex flex-wrap items-center gap-5 pt-1"
              >
                <label className="inline-flex cursor-pointer items-center gap-2 text-xs text-[var(--yak-components-panel-text)]">
                  <Radio value="true" />
                  <span>是</span>
                </label>
                <label className="inline-flex cursor-pointer items-center gap-2 text-xs text-[var(--yak-components-panel-text)]">
                  <Radio value="false" />
                  <span>否</span>
                </label>
              </RadioGroup>
            </FormRow>
          </div>
        </div>
      </section>
    </div>
  );
}
