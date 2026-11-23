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

/**
 * Restrict domains only when the business category has an explicit association.
 * null means relations are unresolved (loading or failed): never fall back to
 * all domains before a successful response.
 */
export function availableDataDomains(
  allDomains: readonly ModelingRecord[],
  businessTypeId: string,
  relatedDomains: readonly ModelingRecord[] | null,
): ModelingRecord[] {
  if (!businessTypeId) return [...allDomains];
  if (relatedDomains === null) return [];
  if (relatedDomains.length === 0) return [...allDomains];
  const linkedIds = new Set(relatedDomains.map((domain) => String(domain.id || "")).filter(Boolean));
  return allDomains.filter((domain) => linkedIds.has(String(domain.id || "")));
}
