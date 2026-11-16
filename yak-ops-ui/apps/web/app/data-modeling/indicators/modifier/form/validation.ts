export function validateModifierSubmit(values: Record<string, string>) {
  if (values.modifierType !== "DIMENSION") return undefined;
  const requiredFields = [
    ["dimensionTbId", "关联维度表"],
    ["dimensionFdId", "关联维度字段"],
    ["codeId", "关联标准代码"],
    ["codeDetailId", "关联代码明细"],
  ] as const;
  const missing = requiredFields.find(([key]) => !String(values[key] || "").trim());
  return missing ? "维度枚举修饰词提交前，请完成" + missing[1] : undefined;
}
