import type { DataSourceCatalogColumn } from "@/service/datasource";
import type { DataSyncTableMapping } from "@/service/data-sync";
import {
  validateBatchSize,
  validateTableDml,
  validateTableSnapshot,
} from "@/service/data-sync/table-configuration";
import { tableKey } from "./table-catalog";

export interface ReplicationMetadata {
  columns: Map<string, DataSourceCatalogColumn[] | undefined>;
  loading: boolean;
  error: string;
  refresh: () => void;
}

/** Physical column checks guide configuration; publication validates actual sink constraints. */
export function replicationConfigurationError(
  mappings: DataSyncTableMapping[],
  metadata: ReplicationMetadata,
  initialLoad: boolean,
  continuous: boolean,
  evolution: boolean,
  targetType: string,
): string {
  if (metadata.error) return metadata.error;
  if (metadata.loading) return "正在读取来源字段";
  for (const mapping of mappings) {
    try {
      validateTableSnapshot(mapping.source.snapshot);
      validateTableDml(mapping.source.dml);
      validateBatchSize(mapping.batchSize);
    } catch (failure) {
      return `${mapping.source.table}：${(failure as Error).message}`;
    }
    if (mapping.writeMode === "RETRACT" && mapping.batchSize !== undefined)
      return "逐行更新不支持自定义写入批次大小";
    if (evolution && mapping.target.primaryKeys?.length)
      return "自定义目标主键需先关闭同步结构变更";
    const columns = metadata.columns.get(tableKey(mapping.source));
    if (!columns?.length) return "请加载所选来源表的字段";
    const hasPrimaryKey = columns.some((column) => column.primaryKey === true);
    if (
      mapping.writeMode === "RETRACT" &&
      (mappings.length !== 1 ||
        targetType !== "MYSQL" ||
        !continuous ||
        evolution ||
        mapping.target.primaryKeys?.length)
    )
      return "逐行更新仅支持单张 MySQL 目标表的持续同步，需关闭结构变更且不设置目标主键";
    if (hasPrimaryKey) continue;
    if (!initialLoad)
      return `${mapping.source.table} 没有物理主键，不支持仅增量；请选择包含全量的策略`;
    if (mappings.length > 1)
      return `${mapping.source.table} 没有物理主键，请单独选择该表配置行标识`;
    if (evolution) return "无主键来源表需关闭同步结构变更";
    const chunk = columns.find((column) => column.name === mapping.source.snapshot?.chunkKeyColumn);
    if (
      !chunk ||
      chunk.nullable !== false ||
      !/^(tinyint|smallint|int(?:eger)?|bigint|decimal|numeric)(\b|\()/i.test(chunk.typeName || "")
    )
      return "无主键来源表需选择非空数值字段作为快照分片字段";
    const keys = mapping.target.primaryKeys;
    if (
      mapping.writeMode === "UPSERT" &&
      (!keys?.length || keys.some((key) => !key.trim()) || new Set(keys).size !== keys.length)
    )
      return "按主键更新需明确目标主键；目标唯一性和非空约束将在上线时验证";
  }
  return "";
}
