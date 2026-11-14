import type { ModelingRecord, ModelingResourceKey } from "../../../service";
import type { IndicatorField, IndicatorFieldOption, IndicatorKind } from "../types";

export function recordName(record: ModelingRecord): string {
  const candidates = [record.businessName, record.domainName, record.martName, record.processName, record.cnName, record.name, record.enShortName];
  const value = candidates.find((item) => item !== undefined && item !== null && String(item).trim() !== "");
  return value === undefined ? String(record.id || "未命名") : String(value);
}

export function readFormValue(record: ModelingRecord | undefined, key: string): string {
  const value = record?.[key];
  if (value === undefined || value === null) return "";
  return typeof value === "boolean" ? String(value) : String(value);
}

export function initialFormValues(
  kind: IndicatorKind,
  record: ModelingRecord | undefined,
  fields: IndicatorField[],
): Record<string, string> {
  const values = Object.fromEntries(fields.map((field) => [field.key, readFormValue(record, field.key)]));
  if (!record && kind === "atomIndicator") {
    values.dwType = "PUBLIC_LAYER";
    values.removeDuplicates = "true";
    values.decimalNumber = "0";
  }
  if (!record && kind === "composeIndicator") values.computeMode = "0";
  if (!record && kind === "modifier") values.modifierType = "SIMPLE";
  return values;
}

export function selectOptionLabel(
  options: IndicatorFieldOption[],
  value: unknown,
  placeholder: string,
  fallbackLabel = "未知选项",
): string {
  if (value === undefined || value === null || value === "") return placeholder;
  return options.find((option) => option.value === String(value))?.label || fallbackLabel;
}

export function fieldOptions(
  field: IndicatorField,
  optionsByResource: Partial<Record<ModelingResourceKey, ModelingRecord[]>>,
): IndicatorFieldOption[] {
  if (field.options) return field.options;
  if (!field.optionsSource) return [];
  const labelField = field.optionLabelField || "cnName";
  return (optionsByResource[field.optionsSource] || []).map((record) => {
    const label = record[labelField] ?? record.cnName ?? record.businessName ?? record.id;
    const shortName = record.enShortName ? " (" + String(record.enShortName) + ")" : "";
    return { value: String(record.id || ""), label: String(label || "未命名") + shortName };
  }).filter((item) => item.value);
}
