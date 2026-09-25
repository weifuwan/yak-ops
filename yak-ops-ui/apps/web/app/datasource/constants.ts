export interface DataSourceOption {
  label: string;
  value: string;
}

export const DATA_SOURCE_PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

export const COMMON_DB_OPTIONS: DataSourceOption[] = [
  { label: "MySQL", value: "MYSQL" },
  { label: "Oracle", value: "ORACLE" },
  { label: "PostgreSQL", value: "POSTGRE_SQL" },
];

export const JDBC_URL_PLACEHOLDERS: Record<string, string> = {
  MYSQL: "jdbc:mysql://127.0.0.1:3306/yak",
  ORACLE: "jdbc:oracle:thin:@//127.0.0.1:1521/orcl",
  POSTGRE_SQL: "jdbc:postgresql://127.0.0.1:5432/yak",
};

export const CONNECTION_STATUS_OPTIONS = [
  { value: "CONNECTED", messageId: "pages.datasource.status.connected" },
  { value: "DISCONNECTED", messageId: "pages.datasource.status.disconnected" },
  { value: "UNKNOWN", messageId: "pages.datasource.status.unknown" },
];
