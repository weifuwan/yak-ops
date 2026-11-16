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
  if (!values.domainId || !values.busProcessId) {
    return "请选择业务过程";
  }

  const decimalNumber = values.decimalNumber.trim();
  if (decimalNumber && (!/^\d+$/.test(decimalNumber) || Number(decimalNumber) < 0)) {
    return "小数位数必须是大于等于0的整数";
  }

  const calculateFunction = values.calculateFunction.trim().toUpperCase();
  if (calculateFunction && !CALCULATE_FUNCTIONS.has(calculateFunction)) {
    return "请选择有效的计算函数";
  }

  return undefined;
}
