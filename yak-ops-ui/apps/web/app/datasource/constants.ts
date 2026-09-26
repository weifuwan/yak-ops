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

export const JDBC_PROPERTY_SUGGESTIONS: Record<string, string[]> = {
  MYSQL: [
    "useUnicode",
    "characterEncoding",
    "serverTimezone",
    "useSSL",
    "sslMode",
    "requireSSL",
    "allowPublicKeyRetrieval",
    "connectTimeout",
    "socketTimeout",
  ],
  ORACLE: ["oracle.net.CONNECT_TIMEOUT", "oracle.jdbc.ReadTimeout", "defaultRowPrefetch"],
  POSTGRE_SQL: ["sslmode", "connectTimeout", "socketTimeout", "ApplicationName", "currentSchema"],
};
