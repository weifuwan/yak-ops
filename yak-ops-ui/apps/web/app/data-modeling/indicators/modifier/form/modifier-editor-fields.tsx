import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, ChevronUp } from "lucide-react";
import { listUsers, type UserRecord } from "@/service/user";
import {
  Field, FieldLabel, Input, Radio, RadioGroup, Select, SelectContent, SelectItem,
  SelectItemIndicator, SelectItemText, SelectTrigger, SelectValue, Textarea,
} from "@yak-ops/yak-ui";

import { businessTypeApi } from "@/service/data-modeling/planning/business-type";
import { dimensionReferenceApi, type ModelingDimensionFieldRecord } from "@/service/data-modeling/indicators/dimension-reference";
import { codeDetailApi } from "@/service/data-modeling/standards/code-standard";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import { IndicatorSectionHeader } from "../../workbench/components/workspace/indicator-section-header";
import type { IndicatorFieldOption, IndicatorOptionResource } from "../../workbench/types";
import { fieldOptions, selectOptionLabel } from "../../workbench/utils/indicator-form";

type Props = {
  projectCode: string;
  values: Record<string, string>;
  recordId: string;
  optionsByResource: Partial<Record<IndicatorOptionResource, ModelingRecord[]>>;
  optionsLoading: boolean;
  onChange: (key: string, value: string) => void;
  onChangeMany: (values: Record<string, string>) => void;
};

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
      <FieldLabel required={required} className="shrink-0 pt-1.5 text-xs font-normal leading-5 text-[var(--yak-components-muted-text)]">
        {label}
      </FieldLabel>
      <div className="min-w-0">{children}</div>
    </Field>
  );
}

function OptionSelect({
  value,
  options,
  placeholder,
  disabled = false,
  loading = false,
  onChange,
}: {
  value: string;
  options: IndicatorFieldOption[];
  placeholder: string;
  disabled?: boolean;
  loading?: boolean;
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
      <SelectContent emptyContent={loading ? "正在加载选项..." : "暂无可选数据"}>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            <SelectItemText>{option.label}</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

export function ModifierEditorFields({
  projectCode,
  values,
  recordId,
  optionsByResource,
  optionsLoading,
  onChange,
  onChangeMany,
}: Props) {
  const [advancedOpen, setAdvancedOpen] = useState(true);
  const isDimension = values.modifierType === "DIMENSION";

  const dimensionOptionsQuery = useQuery({
    queryKey: ["modeling", "modifier-dimension-options", projectCode],
    queryFn: () => dimensionReferenceApi.dimensionOptions(projectCode),
    enabled: Boolean(projectCode && isDimension),
  });
  const dimensionFieldsQuery = useQuery<ModelingDimensionFieldRecord[]>({
    queryKey: ["modeling", "modifier-dimension-fields", projectCode, values.dimensionTbId],
    queryFn: () => dimensionReferenceApi.fields(projectCode, values.dimensionTbId),
    enabled: Boolean(projectCode && isDimension && values.dimensionTbId),
  });
  const relatedDomainsQuery = useQuery<Record<string, string[]>>({
    queryKey: ["modeling", "modifier-business-domains", projectCode],
    queryFn: () => businessTypeApi.allRelatedDomainIds(projectCode),
    enabled: Boolean(projectCode),
  });
  const ownerOptionsQuery = useQuery<UserRecord[]>({
    queryKey: ["modeling", "modifier-owner-options", projectCode],
    queryFn: async () => {
      try {
        const firstPage = await listUsers({ pageNo: 1, pageSize: 100 });
        if (firstPage.totalPage <= 1) return firstPage.records;
        const remainingPages = await Promise.all(
          Array.from({ length: firstPage.totalPage - 1 }, (_, index) =>
            listUsers({ pageNo: index + 2, pageSize: 100 }),
          ),
        );
        return [...firstPage.records, ...remainingPages.flatMap((page) => page.records)];
      } catch {
        // The user-list API is admin-only; keep manual entry available for other roles.
        return [];
      }
    },
    enabled: Boolean(projectCode),
  });

  const dimensionFields = dimensionFieldsQuery.data || [];
  const selectedDimensionField = dimensionFields.find((field) => String(field.id || "") === values.dimensionFdId);
  const effectiveCodeId = String(selectedDimensionField?.codeStdId || values.codeId || "");
  const codeDetailsQuery = useQuery<ModelingRecord[]>({
    queryKey: ["modeling", "modifier-code-details", projectCode, effectiveCodeId],
    queryFn: () => codeDetailApi.all<ModelingRecord>(projectCode, { codeId: effectiveCodeId }),
    enabled: Boolean(projectCode && isDimension && effectiveCodeId),
  });

  const typeOptions: IndicatorFieldOption[] = [
    { value: "SIMPLE", label: "普通业务修饰词" },
    { value: "DIMENSION", label: "维度枚举修饰词" },
  ];
  const currentOdsLayer = (optionsByResource.dwLayer || []).find((record) =>
    String(record.id || "") === values.dwhId && String(record.dwType || "") === "SOURCE_LAYER",
  );
  const layerOptions: IndicatorFieldOption[] = [
    { value: "__NO_LAYER__", label: "不关联数仓分层" },
    ...fieldOptions({
      key: "dwhId",
      label: "数仓分层",
      optionsSource: "dwLayer",
      excludeOptionValues: ["SOURCE_LAYER"],
      optionLabelPrefixField: "dwTypeName",
    }, optionsByResource),
    ...(currentOdsLayer ? [{
      value: values.dwhId,
      label: "ODS 贴源层（当前记录已关联，请清除）",
      disabled: true,
    }] : []),
  ];
  const businessTypeOptions = fieldOptions({
    key: "busTypeId",
    label: "业务分类",
    optionsSource: "businessType",
    optionLabelField: "businessName",
  }, optionsByResource);
  const allDomainOptions = fieldOptions({
    key: "domainId",
    label: "数据域",
    optionsSource: "dataDomain",
  }, optionsByResource);
  const relatedDomainIds = values.busTypeId ? relatedDomainsQuery.data?.[values.busTypeId] || [] : [];
  const domainOptions = relatedDomainIds.length
    ? allDomainOptions.filter((option) => relatedDomainIds.includes(option.value))
    : allDomainOptions;
  const codeOptions = fieldOptions({
    key: "codeId",
    label: "标准代码",
    optionsSource: "codeStandard",
  }, optionsByResource);
  const ownerOptions: IndicatorFieldOption[] = (ownerOptionsQuery.data || []).map((user) => ({
    value: user.userName,
    label: user.userName,
  }));
  if (values.director && !ownerOptions.some((option) => option.value === values.director)) {
    ownerOptions.unshift({ value: values.director, label: values.director });
  }
  const dimensionTableOptions: IndicatorFieldOption[] = (dimensionOptionsQuery.data || []).map((option) => ({
    value: String(option.value),
    label: String(option.label),
  }));
  const dimensionFieldOptions: IndicatorFieldOption[] = dimensionFields.map((field) => ({
    value: String(field.id || ""),
    label: field.cnName && field.fieldName && field.fieldName !== field.cnName
      ? String(field.cnName) + "（" + String(field.fieldName) + "）"
      : String(field.cnName || field.fieldName || "未命名"),
  })).filter((option) => option.value);
  const codeDetailOptions: IndicatorFieldOption[] = (codeDetailsQuery.data || []).map((record) => ({
    value: String(record.id || ""),
    label: String(record.cnName || record.enName || record.enShortName || record.id || "未命名"),
  })).filter((option) => option.value);

  const changeModifierType = (value: string) => {
    if (value === "SIMPLE") {
      onChangeMany({
        modifierType: value,
        dimensionTbId: "",
        dimensionFdId: "",
        codeId: "",
        codeDetailId: "",
      });
    } else {
      onChange("modifierType", value);
    }
  };

  const changeBusinessType = (value: string) => {
    const next: Record<string, string> = { busTypeId: value };
    const nextRelatedDomainIds = relatedDomainsQuery.data?.[value] || [];
    if (values.domainId && nextRelatedDomainIds.length && !nextRelatedDomainIds.includes(values.domainId)) {
      next.domainId = "";
    }
    onChangeMany(next);
  };

  const changeDimensionTable = (value: string) => {
    onChangeMany({
      dimensionTbId: value,
      dimensionFdId: "",
      codeId: "",
      codeDetailId: "",
    });
  };

  const changeDimensionField = (value: string) => {
    const field = dimensionFields.find((item) => String(item.id || "") === value);
    onChangeMany({
      dimensionFdId: value,
      codeId: String(field?.codeStdId || ""),
      codeDetailId: "",
    });
  };

  return (
    <section className="bg-white p-4">
      <IndicatorSectionHeader title="修饰词基本信息" />

      <div className="space-y-3">
        <FormRow label="修饰词类型" required>
          <RadioGroup
            aria-label="修饰词类型"
            value={values.modifierType || "SIMPLE"}
            onValueChange={(value) => changeModifierType(String(value))}
            className="flex flex-wrap items-center gap-5 pt-1"
          >
            {typeOptions.map((option) => (
              <label key={option.value} htmlFor={"modifier-type-" + option.value} className="inline-flex cursor-pointer items-center gap-2 text-xs text-[var(--yak-components-panel-text)]">
                <Radio id={"modifier-type-" + option.value} value={option.value} />
                <span>{option.label}</span>
              </label>
            ))}
          </RadioGroup>
        </FormRow>

        <FormRow label="英文缩写" required>
          <Input
            size="small"
            variant="outlined"
            value={values.enShortName || ""}
            disabled={Boolean(recordId)}
            placeholder="请输入英文缩写"
            onChange={(event) => onChange("enShortName", event.target.value)}
          />
          <div className="mt-1.5 flex items-center gap-1.5 rounded bg-[#fff7e8] px-2 py-1 text-[11px] leading-4 text-[#9a6b22]">
            <span aria-hidden="true">⚠</span>
            英文缩写是修饰词的唯一性标识，一经保存，无法二次修改。
          </div>
        </FormRow>

        <FormRow label="英文名称">
          <Input size="small" variant="outlined" value={values.enName || ""} placeholder="请输入英文名称" onChange={(event) => onChange("enName", event.target.value)} />
        </FormRow>

        <FormRow label="中文名称" required>
          <Input size="small" variant="outlined" value={values.cnName || ""} placeholder="请输入中文名称" onChange={(event) => onChange("cnName", event.target.value)} />
        </FormRow>

        <FormRow label="业务口径" required>
          <Textarea size="small" variant="outlined" rows={3} value={values.businessCaliber || ""} placeholder="请输入业务口径" onValueChange={(value) => onChange("businessCaliber", String(value || ""))} />
        </FormRow>

        <FormRow label="负责人" required>
          {ownerOptionsQuery.isFetching || ownerOptionsQuery.data?.length ? (
            <OptionSelect
              value={values.director || ""}
              options={ownerOptions}
              placeholder="请选择负责人"
              loading={ownerOptionsQuery.isFetching}
              onChange={(value) => onChange("director", value)}
            />
          ) : (
            <Input
              size="small"
              variant="outlined"
              value={values.director || ""}
              placeholder="请输入负责人"
              onChange={(event) => onChange("director", event.target.value)}
            />
          )}
        </FormRow>

        <FormRow label="描述">
          <Textarea size="small" variant="outlined" rows={3} value={values.remark || ""} placeholder="请输入描述" onValueChange={(value) => onChange("remark", String(value || ""))} />
        </FormRow>
      </div>

      <div className="mt-4 border-t border-[var(--yak-components-panel-border)] pt-3">
        <button
          type="button"
          aria-expanded={advancedOpen}
          className="inline-flex items-center gap-1 text-xs font-medium text-[#4169df] transition-colors hover:text-[#3156c9]"
          onClick={() => setAdvancedOpen((open) => !open)}
        >
          {advancedOpen ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
          {advancedOpen ? "隐藏高级配置" : "显示高级配置"}
        </button>

        {advancedOpen ? (
          <div className="mt-3 space-y-3">
            <FormRow label="数仓分层">
              <OptionSelect
                value={values.dwhId || ""}
                options={layerOptions}
                placeholder="请选择数仓分层"
                loading={optionsLoading}
                onChange={(value) => onChange("dwhId", value === "__NO_LAYER__" ? "" : value)}
              />
              {currentOdsLayer ? <p className="mt-1 text-[11px] text-[#b45309]">当前记录关联了 ODS 贴源层，该关联已不再允许使用，请清除或更换数仓分层后保存。</p> : null}
            </FormRow>
            <FormRow label="业务分类">
              <OptionSelect value={values.busTypeId || ""} options={businessTypeOptions} placeholder="请选择业务分类" loading={optionsLoading} onChange={changeBusinessType} />
            </FormRow>
            <FormRow label="数据域">
              <OptionSelect value={values.domainId || ""} options={domainOptions} placeholder="请选择数据域" loading={optionsLoading || relatedDomainsQuery.isFetching} onChange={(value) => onChange("domainId", value)} />
              {relatedDomainIds.length ? <p className="mt-1 text-[11px] text-[var(--yak-components-muted-text)]">已按所选业务分类关联的数据域过滤。</p> : null}
            </FormRow>

            {isDimension ? (
              <>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-[116px_minmax(0,1fr)] sm:gap-3">
                  <div className="pt-1.5 text-xs leading-5 text-[var(--yak-components-muted-text)]">关联维度：</div>
                  <div className="grid min-w-0 grid-cols-1 gap-2 sm:grid-cols-2">
                    <OptionSelect value={values.dimensionTbId || ""} options={dimensionTableOptions} placeholder="请选择维度表" loading={dimensionOptionsQuery.isFetching} onChange={changeDimensionTable} />
                    <OptionSelect value={values.dimensionFdId || ""} options={dimensionFieldOptions} placeholder="请选择维度字段" disabled={!values.dimensionTbId} loading={dimensionFieldsQuery.isFetching} onChange={changeDimensionField} />
                  </div>
                </div>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-[116px_minmax(0,1fr)] sm:gap-3">
                  <div className="pt-1.5 text-xs leading-5 text-[var(--yak-components-muted-text)]">关联标准代码：</div>
                  <div className="grid min-w-0 grid-cols-1 gap-2 sm:grid-cols-2">
                    <OptionSelect value={effectiveCodeId} options={codeOptions} placeholder="请选择标准代码" disabled={Boolean(selectedDimensionField?.codeStdId)} loading={optionsLoading} onChange={(value) => onChangeMany({ codeId: value, codeDetailId: "" })} />
                    <OptionSelect value={values.codeDetailId || ""} options={codeDetailOptions} placeholder="请选择具体枚举值" disabled={!effectiveCodeId} loading={codeDetailsQuery.isFetching} onChange={(value) => onChange("codeDetailId", value)} />
                  </div>
                </div>
                {selectedDimensionField?.codeStdId ? (
                  <p className="pl-0 text-[11px] text-[var(--yak-components-muted-text)] sm:pl-[128px]">当前维度字段已关联标准代码，系统会自动带出对应标准代码。</p>
                ) : null}
              </>
            ) : null}
          </div>
        ) : null}
      </div>
    </section>
  );
}
