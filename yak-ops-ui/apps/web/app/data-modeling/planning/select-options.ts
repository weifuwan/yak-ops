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
