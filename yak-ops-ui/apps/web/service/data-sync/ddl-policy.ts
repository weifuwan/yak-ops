import type { DataSyncDdlStrategies, DataSyncDdlAction } from "./types";

/** Public v9 policy key order; the server remains authoritative about available actions. */
export const DDL_FIELDS = [
  { key: "createTable", label: "新建表", help: "运行中新表纳管仅适用于支持正则发现的链路" },
  { key: "dropTable", label: "删除表", help: "当前链路不能安全同步删除目标表" },
  { key: "addColumn", label: "新增列", help: "仅支持普通、可空、非主键字段" },
  { key: "dropColumn", label: "删除列", help: "正常处理会永久删除目标端字段和数据" },
  { key: "renameTable", label: "重命名表", help: "当前来源无法保证目标表身份安全迁移" },
  { key: "renameColumn", label: "重命名列", help: "不能将重命名推断为新增列加删除列" },
  { key: "modifyColumnType", label: "修改列类型", help: "正常处理仅支持已经验证的安全扩宽" },
  { key: "truncateTable", label: "清空表", help: "需要验证执行顺序与崩溃恢复，暂不开放正常处理" },
] as const satisfies ReadonlyArray<{
  key: keyof DataSyncDdlStrategies;
  label: string;
  help: string;
}>;

export const DDL_ACTION_LABELS: Record<DataSyncDdlAction, string> = {
  NORMAL: "正常处理",
  IGNORE: "忽略",
  ERROR: "出错",
};

/**
 * Conservative defaults for *new* v9 tasks until each runtime capability is proven.
 * Destructive TRUNCATE remains ERROR instead of promising an unimplemented action.
 */
export const DEFAULT_DDL_STRATEGIES: Readonly<DataSyncDdlStrategies> = {
  createTable: "ERROR",
  dropTable: "ERROR",
  addColumn: "NORMAL",
  dropColumn: "ERROR",
  renameTable: "ERROR",
  renameColumn: "ERROR",
  modifyColumnType: "NORMAL",
  truncateTable: "ERROR",
};

/** Explicit, acknowledged legacy-to-v9 recommendation; this is not a lossless migration. */
export function recommendLegacyDdlStrategies(
  schemaChangesEnabled: boolean,
  allowDropColumns: boolean,
  dynamicSelection: boolean,
): DataSyncDdlStrategies {
  return {
    ...DEFAULT_DDL_STRATEGIES,
    createTable: dynamicSelection ? "NORMAL" : "ERROR",
    addColumn: schemaChangesEnabled ? "NORMAL" : "ERROR",
    modifyColumnType: schemaChangesEnabled ? "NORMAL" : "ERROR",
    dropColumn: schemaChangesEnabled && allowDropColumns ? "NORMAL" : "ERROR",
  };
}

export function isDdlPolicy(value: unknown): value is DataSyncDdlStrategies {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const obj = value as Record<string, unknown>;
  return (
    Object.keys(obj).length === DDL_FIELDS.length &&
    DDL_FIELDS.every(({ key }) => ["NORMAL", "IGNORE", "ERROR"].includes(String(obj[key])))
  );
}
