import { pageDataSourceTables } from "./index";
import type { DataSourceCatalogTable } from "./types";

const escapePattern = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const names = (table: DataSourceCatalogTable) => [
  table.name,
  [table.schema, table.name].filter(Boolean).join("."),
  [table.database, table.schema, table.name].filter(Boolean).join("."),
];

/** Resolve pasted physical identities against the complete catalog, rejecting ambiguous short names. */
export async function resolvePastedDataSourceTables(
  id: string,
  requested: string[],
  maxTables: number,
  signal?: AbortSignal,
): Promise<DataSourceCatalogTable[]> {
  if (requested.length > maxTables) throw new Error(`最多选择 ${maxTables} 张表`);
  const result: DataSourceCatalogTable[] = [];
  for (const name of requested) {
    const shortName = name.split(".").at(-1)!;
    const found: DataSourceCatalogTable[] = [];
    let offset: number | null = 0;
    while (offset !== null) {
      const page = await pageDataSourceTables(
        id,
        { tablePattern: `^${escapePattern(shortName)}$`, offset, limit: 500 },
        signal,
      );
      found.push(...page.items.filter((table) => names(table).includes(name)));
      if (found.length > 1) break;
      if (page.nextOffset !== null && page.nextOffset <= offset)
        throw new Error("目录分页响应无效");
      offset = page.nextOffset;
    }
    if (!found.length) throw new Error(`库表不存在：${name}`);
    if (found.length > 1) throw new Error(`库表名称不唯一，请填写完整库名：${name}`);
    result.push(found[0]);
  }
  return result;
}
