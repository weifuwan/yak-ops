import type { DataSourceCatalogColumn, DataSourceCatalogTablePath } from "../datasource/types";
import type { DataSyncTransform } from "./types";

export interface DirectFieldMapping {
  source: string;
  target: string;
}

export type DirectProjection = { all: true } | { all: false; fields: DirectFieldMapping[] };

/** Selects the one physical table without treating its identifier as a regular expression. */
export function transformTableSelector(table: DataSourceCatalogTablePath): string {
  return [table.database, table.schema, table.table]
    .filter((part): part is string => Boolean(part))
    .map((part) => part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    .join(".");
}

const quoteColumn = (name: string) => "`" + name.replaceAll("`", "``") + "`";

/** The field table emits only direct, quoted column references and optional aliases. */
export function fieldProjection(fields: readonly DirectFieldMapping[]): string {
  if (fields.length === 0) throw new Error("至少选择一个来源字段");
  return fields
    .map(({ source, target }) =>
      source === target ? quoteColumn(source) : `${quoteColumn(source)} AS ${quoteColumn(target)}`,
    )
    .join(", ");
}

/**
 * Recognizes this editor's direct projection format, not general SQL. The original rule remains
 * authoritative even when recognized; every other expression stays in the expression editor.
 */
export function readDirectProjection(projection?: string): DirectProjection | undefined {
  if (!projection?.trim() || projection.trim() === "*") return { all: true };
  const column = /`((?:``|[^`])*)`(?: AS `((?:``|[^`])*)`)?/gy;
  const fields: DirectFieldMapping[] = [];
  let offset = 0;
  while (offset < projection.length) {
    column.lastIndex = offset;
    const match = column.exec(projection);
    if (!match) return undefined;
    const source = match[1].replaceAll("``", "`");
    const target = (match[2] ?? match[1]).replaceAll("``", "`");
    fields.push({ source, target });
    offset = column.lastIndex;
    if (offset === projection.length) break;
    if (projection.slice(offset, offset + 2) !== ", ") return undefined;
    offset += 2;
    if (offset === projection.length) return undefined;
  }
  if (
    fields.length === 0 ||
    new Set(fields.map((field) => field.source)).size !== fields.length ||
    fieldProjection(fields) !== projection
  )
    return undefined;
  return { all: false, fields };
}

export function canEditFieldMapping(
  rules: readonly DataSyncTransform[],
  selector: string,
): boolean {
  if (rules.length > 1) return false;
  const rule = rules[0];
  return (
    !rule ||
    (rule["source-table"] === selector &&
      !rule["partition-keys"] &&
      !rule["table-options"] &&
      !rule["converter-after-transform"])
  );
}

/** Changes just the authored fields of one rule, retaining all other rules and option text. */
export function updateTransformRule(
  rules: readonly DataSyncTransform[],
  index: number,
  selector: string,
  patch: Partial<DataSyncTransform>,
): DataSyncTransform[] {
  if (index < 0 || index > rules.length) throw new Error("转换规则不存在");
  const replacement = Object.fromEntries(
    Object.entries({ ...(rules[index] ?? { "source-table": selector }), ...patch }).filter(
      ([, value]) => value !== undefined,
    ),
  ) as unknown as DataSyncTransform;
  const next = [...rules];
  next[index] = replacement;
  return next;
}

/** Keep other selectors in place because overlapping rules are evaluated in authored order. */
export function replaceTableTransforms(
  rules: readonly DataSyncTransform[],
  selector: string,
  replacements: readonly DataSyncTransform[],
): DataSyncTransform[] {
  if (replacements.some((rule) => rule["source-table"] !== selector))
    throw new Error("表级规则不能更改来源表范围");
  const last = rules.reduce(
    (found, rule, index) => (rule["source-table"] === selector ? index : found),
    -1,
  );
  if (last < 0) return [...rules, ...replacements];
  const result: DataSyncTransform[] = [];
  let replacement = 0;
  rules.forEach((rule, index) => {
    if (rule["source-table"] !== selector) result.push(rule);
    else if (replacement < replacements.length) result.push(replacements[replacement++]);
    if (index === last) result.push(...replacements.slice(replacement));
  });
  return result;
}

export function sameNameFieldMappings(
  source: readonly DataSourceCatalogColumn[],
  target?: readonly DataSourceCatalogColumn[],
): DirectFieldMapping[] {
  const targetNames = target ? new Set(target.map((column) => column.name)) : undefined;
  return source
    .filter((column) => !targetNames || targetNames.has(column.name))
    .map((column) => ({ source: column.name, target: column.name }));
}

/** Only a complete, directly forwarded physical key can be offered as an output key. */
export function mappedSourcePrimaryKeys(
  fields: readonly DirectFieldMapping[],
  source: readonly DataSourceCatalogColumn[],
): string[] | undefined {
  const primaryKeys = source.filter((column) => column.primaryKey);
  if (primaryKeys.length === 0 || primaryKeys.some((column) => column.nullable !== false))
    return undefined;
  const keys: string[] = [];
  for (const column of primaryKeys) {
    const matches = fields.filter((field) => field.source === column.name);
    if (matches.length !== 1) return undefined;
    const name = matches[0].target;
    if (!name || name !== name.trim() || name.includes(",")) return undefined;
    keys.push(name);
  }
  return new Set(keys).size === keys.length ? keys : undefined;
}

/** Local structural feedback only; expression types and values are checked by the existing engine. */
export function transformEditingError(rules: readonly DataSyncTransform[]): string | undefined {
  for (const rule of rules) {
    if (!rule["source-table"].trim()) return "请填写转换规则的来源表";
    const direct = readDirectProjection(rule.projection);
    if (!direct || direct.all) continue;
    if (direct.fields.some((field) => !field.source || !field.target.trim()))
      return "来源字段和输出字段不能为空";
    const output = direct.fields.map((field) => field.target.toLowerCase());
    if (new Set(output).size !== output.length) return "输出字段名称不能重复";
  }
  return undefined;
}
