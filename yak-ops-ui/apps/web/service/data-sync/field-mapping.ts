import type { DataSourceCatalogColumn } from "../datasource/types";

export interface DirectFieldMapping {
  source: string;
  target: string;
}

export type DirectProjection =
  | { all: true }
  | { all: false; fields: DirectFieldMapping[] };

const quoteColumn = (name: string) => "`" + name.replaceAll("`", "``") + "`";

export function fieldProjection(fields: readonly DirectFieldMapping[]): string {
  if (!fields.length) throw new Error("至少保留一个同步字段");
  if (fields.some(({ source, target }) => !source.trim() || !target.trim())) {
    throw new Error("来源字段和去向字段不能为空");
  }
  const targets = fields.map(({ target }) => target.trim().toLowerCase());
  if (new Set(targets).size !== targets.length) {
    throw new Error("去向字段不能重复");
  }
  return fields
    .map(({ source, target }) =>
      source === target
        ? quoteColumn(source)
        : quoteColumn(source) + " AS " + quoteColumn(target),
    )
    .join(", ");
}

export function readDirectProjection(
  projection?: string,
): DirectProjection | undefined {
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
    !fields.length ||
    new Set(fields.map((field) => field.source)).size !== fields.length ||
    fieldProjection(fields) !== projection
  ) {
    return undefined;
  }

  return { all: false, fields };
}

export function sameNameFieldMappings(
  source: readonly DataSourceCatalogColumn[],
  target?: readonly DataSourceCatalogColumn[],
): DirectFieldMapping[] {
  const targetNames = target
    ? new Set(target.map((column) => column.name))
    : undefined;

  return source
    .filter((column) => !targetNames || targetNames.has(column.name))
    .map((column) => ({ source: column.name, target: column.name }));
}

export function mappedSourcePrimaryKeys(
  fields: readonly DirectFieldMapping[],
  source: readonly DataSourceCatalogColumn[],
): string[] | undefined {
  const primaryKeys = source.filter((column) => column.primaryKey);
  if (!primaryKeys.length) return undefined;

  const keys: string[] = [];
  for (const column of primaryKeys) {
    const mapped = fields.find((field) => field.source === column.name);
    if (!mapped?.target.trim()) return undefined;
    keys.push(mapped.target.trim());
  }

  return new Set(keys).size === keys.length ? keys : undefined;
}
