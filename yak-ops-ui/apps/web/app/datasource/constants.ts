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

export const JDBC_URL_PLACEHOLDERS: Record<string, string> = {
  MYSQL: "jdbc:mysql://127.0.0.1:3306/yak",
  ORACLE: "jdbc:oracle:thin:@//127.0.0.1:1521/orcl",
  POSTGRE_SQL: "jdbc:postgresql://127.0.0.1:5432/yak",
};
