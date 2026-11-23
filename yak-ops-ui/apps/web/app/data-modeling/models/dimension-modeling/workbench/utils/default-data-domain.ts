import type { ModelingRecord } from "@/service/data-modeling/shared/types";

/** Pick the real ID of a system-default domain, otherwise the first available record. */
export function defaultDataDomainId(domains: readonly ModelingRecord[]): string {
  const available = domains.filter((domain) => String(domain.id || "").trim());
  const isSystem = (domain: ModelingRecord) => String(domain.flag) === "1";
  const isDefault = (domain: ModelingRecord) =>
    String(domain.enShortName || "").trim().toLowerCase() === "default";

  const preferred = available.find((domain) => isSystem(domain) && isDefault(domain))
    || available.find(isSystem)
    || available.find(isDefault)
    || available[0];

  return String(preferred?.id || "");
}
