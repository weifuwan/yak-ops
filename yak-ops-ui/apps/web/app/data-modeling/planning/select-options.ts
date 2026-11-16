export interface PlanningSelectOption {
  value: string;
  label: string;
}

export function withSelectedOption(
  options: readonly PlanningSelectOption[],
  selectedValue?: string,
  fallbackLabel?: string,
): PlanningSelectOption[] {
  const value = String(selectedValue || "");
  if (!value || options.some((option) => option.value === value)) {
    return [...options];
  }

  return [...options, { value, label: fallbackLabel || "已关联选项" }];
}

export const MART_TYPE_OPTIONS = [
  { value: "PUBLIC", label: "公共集市" },
  { value: "BUSINESS", label: "业务集市" },
  { value: "APPLICATION", label: "数据应用集市" },
] as const;
