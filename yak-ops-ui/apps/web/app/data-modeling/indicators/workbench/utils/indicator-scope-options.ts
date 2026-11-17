import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type { IndicatorGroupBy } from "../types";

export type IndicatorScopeOption = {
  value: string;
  label: string;
};

const SCOPE_LABEL_FIELDS: Record<IndicatorGroupBy, readonly string[]> = {
  BUSINESS_TYPE: ["businessName", "cnName", "enName"],
  DATA_DOMAIN: ["cnName", "domainName", "enName"],
  DATA_MART: ["martName", "cnName", "enName"],
  SUBJECT_DOMAIN: ["domainName", "cnName", "enName"],
};

export function buildIndicatorScopeOptions(
  records: readonly ModelingRecord[],
  scopeType: IndicatorGroupBy,
): IndicatorScopeOption[] {
  return records.flatMap((record) => {
    const value = String(record.id ?? "");
    if (!value.trim()) return [];

    const labelValue = SCOPE_LABEL_FIELDS[scopeType]
      .map((field) => record[field])
      .find((item) => item !== undefined && item !== null && String(item).trim());

    return [{
      value,
      label: labelValue === undefined ? "未命名" : String(labelValue).trim(),
    }];
  });
}
