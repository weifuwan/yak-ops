export function validateCompositeIndicator(values: Record<string, string>): string | undefined {
  if (!String(values.dwhId || "").trim()) return "请选择数仓分层";
  if (!String(values.enShortName || "").trim()) return "请输入英文缩写";
  if (!String(values.cnName || "").trim()) return "请输入中文名称";
  if (!String(values.director || "").trim()) return "请输入负责人";

  const computeMode = String(values.computeMode || "").trim();
  if (computeMode === "0") {
    if (!String(values.computeExpr || "").trim()) return "请输入计算表达式";
  } else if (computeMode === "1") {
    if (!String(values.deriveId || "").trim()) return "请选择派生指标";
    if (!String(values.compareType || "").trim()) return "请选择对比类型";
  } else {
    return "请选择有效的计算模式";
  }

  const hasPublicOwnership = Boolean(
    String(values.busTypeId || "").trim()
    || String(values.domainId || "").trim()
    || String(values.busProcessId || "").trim(),
  );
  const hasApplicationOwnership = Boolean(
    String(values.martId || "").trim()
    || String(values.subjectId || "").trim(),
  );

  if (hasPublicOwnership && (!String(values.domainId || "").trim() || !String(values.busProcessId || "").trim())) {
    return "公共层必须选择数据域及业务过程";
  }
  if (hasApplicationOwnership && (!String(values.martId || "").trim() || !String(values.subjectId || "").trim())) {
    return "应用层必须选择数据集市及主题域";
  }
  if (!hasPublicOwnership && !hasApplicationOwnership) {
    return "请配置所选数仓分层对应的业务归属";
  }

  return undefined;
}
