export type DataSourceCategory = "RELATIONAL" | "ANALYTICAL" | "OTHER";

export interface DataSourceOption {
  label: string;
  value: string;
  category: DataSourceCategory;
}

export const DATA_SOURCE_PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

// Available datasource processors in yak-ops-datasource-all, not the broader DbType enum.
export const COMMON_DB_OPTIONS: DataSourceOption[] = [
  { label: "MySQL", value: "MYSQL", category: "RELATIONAL" },
  { label: "Oracle", value: "ORACLE", category: "RELATIONAL" },
  { label: "PostgreSQL", value: "POSTGRE_SQL", category: "RELATIONAL" },
  { label: "SQL Server", value: "SQLSERVER", category: "RELATIONAL" },
  { label: "DB2", value: "DB2", category: "RELATIONAL" },
  { label: "Dameng", value: "DAMENG", category: "RELATIONAL" },
  { label: "OceanBase", value: "OCEANBASE", category: "RELATIONAL" },
  { label: "ClickHouse", value: "CLICKHOUSE", category: "ANALYTICAL" },
  { label: "StarRocks", value: "STARROCKS", category: "ANALYTICAL" },
  { label: "Doris", value: "DORIS", category: "ANALYTICAL" },
  { label: "SSH", value: "SSH", category: "OTHER" },
];

export const JDBC_DEFAULT_PORTS: Record<string, number> = {
  MYSQL: 3306,
  ORACLE: 1521,
  POSTGRE_SQL: 5432,
  SQLSERVER: 1433,
  DB2: 50000,
  DAMENG: 5236,
  OCEANBASE: 2881,
  CLICKHOUSE: 8123,
  STARROCKS: 9030,
  DORIS: 9030,
  SSH: 22,
};

export const normalizeDataSourceType = (dbType?: string) => {
  const normalized = String(dbType || "").trim().toUpperCase();
  if (normalized === "POSTGRESQL" || normalized === "POSTGRES") return "POSTGRE_SQL";
  return normalized;
};

export const getDataSourceTypeLabel = (dbType?: string) => {
  const normalized = normalizeDataSourceType(dbType);
  return COMMON_DB_OPTIONS.find((option) => option.value === normalized)?.label || normalized || "-";
};
