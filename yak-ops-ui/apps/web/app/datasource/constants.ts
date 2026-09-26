export type DataSourceCategory = "RELATIONAL";

export interface DataSourceOption {
  label: string;
  value: string;
  category: DataSourceCategory;
}

export const DATA_SOURCE_PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

export const COMMON_DB_OPTIONS: DataSourceOption[] = [
  { label: "MySQL", value: "MYSQL", category: "RELATIONAL" },
  { label: "Oracle", value: "ORACLE", category: "RELATIONAL" },
  { label: "PostgreSQL", value: "POSTGRE_SQL", category: "RELATIONAL" },
];

export const JDBC_DEFAULT_PORTS: Record<string, number> = {
  MYSQL: 3306,
  ORACLE: 1521,
  POSTGRE_SQL: 5432,
};

export const normalizeDataSourceType = (dbType?: string) => {
  const normalized = String(dbType || "")
    .trim()
    .toUpperCase();

  if (normalized === "POSTGRESQL" || normalized === "POSTGRES") return "POSTGRE_SQL";
  return normalized;
};

export const getDataSourceTypeLabel = (dbType?: string) => {
  const normalized = normalizeDataSourceType(dbType);
  return COMMON_DB_OPTIONS.find((option) => option.value === normalized)?.label || normalized || "-";
};
