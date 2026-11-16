import type { ReactNode } from "react";
import {
  Field, FieldLabel, Input, Select, SelectContent, SelectItem, SelectItemIndicator,
  SelectItemText, SelectTrigger, SelectValue, Textarea,
} from "@yak-ops/yak-ui";

import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type {
  IndicatorDefinition,
  IndicatorEditorFieldsProps,
  IndicatorOptionResource,
} from "../../types";
import { fieldOptions, selectOptionLabel } from "../../utils/indicator-form";

type Props = {
  projectCode: string;
  definition: IndicatorDefinition;
  values: Record<string, string>;
  recordId: string;
  optionsByResource: Partial<Record<IndicatorOptionResource, ModelingRecord[]>>;
  optionsLoading: boolean;
  renderFields?: (props: IndicatorEditorFieldsProps) => ReactNode;
  onChange: (key: string, value: string) => void;
  onChangeMany: (values: Record<string, string>) => void;
};

export function IndicatorFormRenderer({
  projectCode, definition, values, recordId, optionsByResource, optionsLoading,
  renderFields, onChange, onChangeMany,
}: Props) {
  if (renderFields) {
    return <>{renderFields({
      projectCode, values, recordId, optionsByResource, optionsLoading, onChange, onChangeMany,
    })}</>;
  }

  const sectionNames = Array.from(new Set(definition.fields.map((field) => field.section || "基本信息")));
  return (
    <>
      {sectionNames.map((section) => (
        <section key={section} className="bg-white p-4">
          <div className="mb-3 flex items-center gap-2 border-b border-[var(--yak-components-panel-border)] pb-2">
            <span aria-hidden="true" className="h-4 w-1 shrink-0 bg-[#5664ff]" />
            <h3 className="m-0 text-sm font-semibold leading-7 text-[var(--yak-components-panel-text)]">{section}</h3>
          </div>
          <div className="grid grid-cols-1 gap-y-3">
            {definition.fields.filter((field) => (field.section || "基本信息") === section).map((field) => {
              const options = fieldOptions(field, optionsByResource);
              const isSelect = Boolean(field.options || field.optionsSource);
              const disabled = Boolean(field.readOnly && recordId);
              const selectPlaceholder = optionsLoading ? "正在加载选项..." : field.placeholder || ("请选择" + field.label);
              return (
                <Field key={field.key} className="grid grid-cols-1 items-start gap-2 sm:grid-cols-[150px_minmax(0,1fr)] sm:gap-3">
                  <FieldLabel required={field.required} className="pt-1.5 text-xs leading-5 text-[var(--yak-components-muted-text)]">{field.label}</FieldLabel>
                  <div className="min-w-0">
                    {isSelect ? (
                      <Select
                        size="small"
                        value={values[field.key] || undefined}
                        disabled={disabled}
                        onValueChange={(value) => onChange(field.key, String(value || ""))}
                      >
                        <SelectTrigger variant="outlined" className="w-full">
                          <SelectValue placeholder={selectPlaceholder}>
                            {(value) => selectOptionLabel(options, value, selectPlaceholder, optionsLoading ? "正在加载选项..." : "未知选项")}
                          </SelectValue>
                        </SelectTrigger>
                        <SelectContent>
                          {options.map((option) => (
                            <SelectItem key={option.value} value={option.value}>
                              <SelectItemText>{option.label}</SelectItemText><SelectItemIndicator />
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    ) : field.multiline ? (
                      <Textarea
                        size="small"
                        variant="outlined"
                        rows={3}
                        value={values[field.key] || ""}
                        disabled={disabled}
                        placeholder={field.placeholder || ("请输入" + field.label)}
                        onValueChange={(value) => onChange(field.key, String(value || ""))}
                      />
                    ) : (
                      <Input
                        size="small"
                        className="w-full"
                        variant="outlined"
                        type={field.key === "decimalNumber" ? "number" : "text"}
                        min={field.key === "decimalNumber" ? 0 : undefined}
                        value={values[field.key] || ""}
                        disabled={disabled}
                        placeholder={field.placeholder || ("请输入" + field.label)}
                        onChange={(event) => onChange(field.key, event.target.value)}
                      />
                    )}
                  </div>
                </Field>
              );
            })}
          </div>
        </section>
      ))}
    </>
  );
}

export default IndicatorFormRenderer;
