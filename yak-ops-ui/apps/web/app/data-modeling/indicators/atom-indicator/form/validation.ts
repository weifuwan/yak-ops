const CALCULATE_FUNCTIONS = new Set([
  "COUNT",
  "SUM",
  "AVG",
  "MAX",
  "MIN",
  "RATE",
  "RANK",
  "OTHER",
]);

export function validateAtomIndicatorSubmit(values: Record<string, string>): string | undefined {
  if (!String(values.domainId || "").trim() || !String(values.busProcessId || "").trim()) {
    return "请选择业务过程";
  }

  const decimalNumber = String(values.decimalNumber || "").trim();
  if (decimalNumber && !/^\d+$/.test(decimalNumber)) {
    return "小数位数必须是大于等于0的整数";
  }

  const calculateFunction = String(values.calculateFunction || "").trim().toUpperCase();
  if (calculateFunction && !CALCULATE_FUNCTIONS.has(calculateFunction)) {
    return "请选择有效的计算函数";
  }

  return undefined;
}
