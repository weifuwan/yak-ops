import type {
  DataSyncTableMapping,
  DataSyncTablePath,
  DataSyncWriteMode,
  DataSyncMappingTemplate,
  DataSyncTableSelection,
} from "@/service/data-sync";
import { templateTarget } from "@/service/data-sync/table-configuration";
import type { DataSourceCatalogTable } from "@/service/datasource";
import { catalogPath, tableKey } from "./table-catalog";

export function addSelectedTables(
  current: DataSyncTableMapping[],
  sources: DataSyncTablePath[],
  targets: DataSourceCatalogTable[],
  namespace: Omit<DataSyncTablePath, "table">,
  writeMode: DataSyncWriteMode,
  prepareSchema: boolean,
  maxTables: number,
): DataSyncTableMapping[] {
  const keys = new Set(current.map(({ source }) => tableKey(source)));
  const additions = sources.filter((source) => {
    const key = tableKey(source);
    if (keys.has(key)) return false;
    keys.add(key);
    return true;
  });
  if (keys.size > maxTables) throw new Error(`最多选择 ${maxTables} 张表`);
  if (current.some((mapping) => mapping.writeMode === "RETRACT") && additions.length)
    throw new Error("逐行更新仅支持单表");
  return [
    ...current,
    ...additions.map((source) => {
      const candidates = targets.filter((target) => target.name === source.table);
      const target =
        candidates.length === 1
          ? catalogPath(candidates[0])
          : { ...namespace, table: prepareSchema ? source.table : "" };
      return { source: { ...source }, target, writeMode };
    }),
  ];
}

export function validTableMappings(
  mappings: DataSyncTableMapping[],
  targetTables: DataSourceCatalogTable[],
  writeModes: DataSyncWriteMode[],
  prepareSchema = false,
  maxTables = 100,
) {
  if (!mappings.length || mappings.length > maxTables) return false;
  const sources = new Set(mappings.map((mapping) => tableKey(mapping.source)));
  const targets = new Set(mappings.map((mapping) => tableKey(mapping.target)));
  const available = new Set(targetTables.map((table) => tableKey(catalogPath(table))));
  return (
    sources.size === mappings.length &&
    targets.size === mappings.length &&
    mappings.every(
      (mapping) =>
        writeModes.includes(mapping.writeMode) &&
        Boolean(mapping.source.table.trim()) &&
        Boolean(mapping.target.table.trim()) &&
        mapping.target.table.length <= 128 &&
        (prepareSchema || available.has(tableKey(mapping.target))),
    )
  );
}

export function parsePastedTableNames(text: string): string[] {
  return [
    ...new Set(
      text
        .split(/[\n\r,]+/)
        .map((name) => name.trim())
        .filter(Boolean),
    ),
  ];
}

/** The matched baseline owns overrides; newly discovered tables use only authored defaults. */
export function resolveMatchedMappings(
  current: DataSyncTableMapping[],
  tables: DataSourceCatalogTable[],
  selection: DataSyncTableSelection,
  template: DataSyncMappingTemplate,
): DataSyncTableMapping[] {
  if (!tables.length) throw new Error("正则未匹配到库表");
  const previous = new Map(current.map((mapping) => [tableKey(mapping.source), mapping]));
  const mappings = tables.map((table) => {
    const source = catalogPath(table);
    return (
      previous.get(tableKey(source)) || {
        source: {
          ...source,
          ...(selection.snapshot ? { snapshot: structuredClone(selection.snapshot) } : {}),
          ...(selection.dml ? { dml: structuredClone(selection.dml) } : {}),
        },
        target: templateTarget(template, source),
        writeMode: template.write.mode,
        ...(template.write.batchSize !== undefined ? { batchSize: template.write.batchSize } : {}),
      }
    );
  });
  if (new Set(mappings.map((mapping) => tableKey(mapping.source))).size !== mappings.length)
    throw new Error("目录返回了重复来源表");
  if (new Set(mappings.map((mapping) => tableKey(mapping.target))).size !== mappings.length)
    throw new Error("目标映射产生重复表，请调整模板");
  return mappings;
}
