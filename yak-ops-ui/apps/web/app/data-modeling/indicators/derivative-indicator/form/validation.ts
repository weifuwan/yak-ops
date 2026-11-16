export function validateDerivativeIndicator(values: Record<string, string>): string | undefined {
  if (!String(values.atomicId || "").trim()) return "请选择原子指标";
  if (!String(values.timePeriodId || "").trim()) return "请选择时间周期";
  if (!String(values.enShortName || "").trim()) return "请输入英文缩写";
  if (!String(values.cnName || "").trim()) return "请输入中文名称";

  const dwType = String(values.dwType || "").trim();
  if (dwType === "PUBLIC_LAYER") {
    if (!String(values.domainId || "").trim() || !String(values.busProcessId || "").trim()) {
      return "公共层必须选择数据域及业务过程";
    }
  }
  if (dwType === "APPLICATION_LAYER") {
    if (!String(values.martId || "").trim()) return "应用层必须选择数据集市";
    if (!String(values.subjectId || "").trim()) return "应用层必须选择主题域";
  }
  // Null and legacy unsupported layer codes are normalized to no layer by the save payload.
  return undefined;
}
