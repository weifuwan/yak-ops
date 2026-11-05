import {
  COMMON_DB_OPTIONS,
  JDBC_DEFAULT_PORTS,
  normalizeDataSourceType,
  type DataSourceCategory,
} from "../constants";
import type { UserPreferenceRecord } from "@/service/preference";
import type { DataSourceRecord } from "@/service/datasource";

export interface DataSourceFormProps {
  open: boolean;
  record?: DataSourceRecord;
  onOpenChange: (open: boolean) => void;
  onSaved: () => void;
}

export interface JdbcProperty {
  key: string;
  value: string;
}

export type MySqlDriverId = "AUTO" | "MYSQL_8" | "MYSQL_5";

export interface FormValues {
  name: string;
  dbType: string;
  jdbcUrl: string;
  host: string;
  port: string;
  database: string;
  username: string;
  password: string;
  driverId: MySqlDriverId;
  compatibleMode: "mysql" | "oracle";
  privateKey: string;
  properties: JdbcProperty[];
  remark: string;
}

export type FormErrorKey =
  | "name"
  | "dbType"
  | "jdbcUrl"
  | "host"
  | "port"
  | "database"
  | "username"
  | "compatibleMode"
  | "properties"
  | "remark";
export type FormErrors = Partial<Record<FormErrorKey, string>>;
export type CreateStep = "select" | "config";
export type CreateCategory = "ALL" | DataSourceCategory;

interface ParsedJdbcUrl {
  host?: string;
  port?: string;
  database?: string;
}

export const DEFAULT_HOST = "127.0.0.1";
export const DATASOURCE_CREATE_TYPE_SCENE = "DATASOURCE_CREATE_TYPE" as const;
const FREQUENT_TYPE_LIMIT = 3;

const preferenceTimestamp = (value?: string | null) => {
  if (!value) return 0;
  const timestamp = Date.parse(value);
  return Number.isNaN(timestamp) ? 0 : timestamp;
};

export const upsertPreference = (
  preferences: UserPreferenceRecord[],
  nextPreference: UserPreferenceRecord,
) => {
  const exists = preferences.some((preference) => preference.itemKey === nextPreference.itemKey);
  if (!exists) return [...preferences, nextPreference];
  return preferences.map((preference) =>
    preference.itemKey === nextPreference.itemKey ? nextPreference : preference,
  );
};

export const frequentDataSourceOptions = (preferences: UserPreferenceRecord[]) => {
  const usedValues = new Set<string>();
  const ranked = [...preferences]
    .filter((preference) => preference.useCount > 0)
    .sort(
      (left, right) =>
        right.useCount - left.useCount ||
        preferenceTimestamp(right.lastUsedTime) - preferenceTimestamp(left.lastUsedTime),
    )
    .flatMap((preference) => {
      const value = normalizeDataSourceType(preference.itemKey);
      const option = COMMON_DB_OPTIONS.find((candidate) => candidate.value === value);
      if (!option || usedValues.has(option.value)) return [];
      usedValues.add(option.value);
      return [option];
    });

  return [...ranked, ...COMMON_DB_OPTIONS.filter((option) => !usedValues.has(option.value))].slice(
    0,
    FREQUENT_TYPE_LIMIT,
  );
};

export const defaultPort = (dbType: string) =>
  String(JDBC_DEFAULT_PORTS[normalizeDataSourceType(dbType)] || "");

const normalizeMySqlDriverId = (value: unknown): MySqlDriverId => {
  if (typeof value !== "string") return "AUTO";
  const normalized = value.trim().toUpperCase();
  if (normalized === "MYSQL_8" || normalized === "MYSQL_5") return normalized;
  return "AUTO";
};

export const EMPTY_FORM: FormValues = {
  name: "",
  dbType: "MYSQL",
  jdbcUrl: "",
  host: DEFAULT_HOST,
  port: defaultPort("MYSQL"),
  database: "",
  username: "",
  password: "",
  driverId: "AUTO",
  compatibleMode: "mysql",
  privateKey: "",
  properties: [],
  remark: "",
};

const parseJdbcUrl = (dbType: string, jdbcUrl?: string): ParsedJdbcUrl => {
  if (!jdbcUrl) return {};
  const value = jdbcUrl.trim();
  const normalizedType = normalizeDataSourceType(dbType);
  const patterns: Record<string, RegExp> = {
    MYSQL: /^jdbc:mysql:\/\/(\[[^\]]+\]|[^:/?#]+)(?::(\d+))?\/([^?]+)(?:\?.*)?$/i,
    ORACLE: /^jdbc:oracle:thin:@\/\/(\[[^\]]+\]|[^:/?#]+)(?::(\d+))?\/([^?]+)(?:\?.*)?$/i,
    POSTGRE_SQL: /^jdbc:postgresql:\/\/(\[[^\]]+\]|[^:/?#]+)(?::(\d+))?\/([^?]+)(?:\?.*)?$/i,
  };
  const pattern = patterns[normalizedType];
  if (!pattern) return {};
  const matched = value.match(pattern);
  if (!matched) return {};
  return {
    host: matched[1],
    port: matched[2] || defaultPort(normalizedType),
    database: matched[3],
  };
};

const parseProperties = (value: unknown): JdbcProperty[] => {
  if (!value || typeof value !== "object" || Array.isArray(value)) return [];
  return Object.entries(value as Record<string, unknown>).map(([key, propertyValue]) => ({
    key,
    value: propertyValue == null ? "" : String(propertyValue),
  }));
};

export const parseOriginalJson = (record?: DataSourceRecord): Partial<FormValues> => {
  const dbType = normalizeDataSourceType(record?.dbType) || "MYSQL";
  const recordJdbcUrl = record?.jdbcUrl?.trim() || "";
  if (!record?.originalJson) {
    if (dbType === "ORACLE") return { jdbcUrl: recordJdbcUrl };
    const jdbc = parseJdbcUrl(dbType, recordJdbcUrl);
    return {
      ...jdbc,
      port: jdbc.port || defaultPort(dbType),
    };
  }
  try {
    const value = JSON.parse(record.originalJson);
    if (!value || typeof value !== "object" || Array.isArray(value)) return {};
    const explicitJdbcUrl =
      typeof value.jdbcUrl === "string" && value.jdbcUrl.trim()
        ? value.jdbcUrl.trim()
        : recordJdbcUrl;

    if (dbType === "ORACLE") {
      const host = typeof value.host === "string" ? value.host.trim() : "";
      const port =
        typeof value.port === "number" || typeof value.port === "string"
          ? String(value.port)
          : defaultPort("ORACLE");
      const database = typeof value.database === "string" ? value.database.trim() : "";
      const legacyJdbcUrl =
        host && database ? "jdbc:oracle:thin:@//" + host + ":" + port + "/" + database : "";
      return {
        jdbcUrl: explicitJdbcUrl || legacyJdbcUrl,
        username: typeof value.username === "string" ? value.username : undefined,
        password: typeof value.password === "string" ? value.password : undefined,
        properties: parseProperties(value.properties),
      };
    }

    const jdbc = parseJdbcUrl(dbType, explicitJdbcUrl);
    return {
      host: typeof value.host === "string" && value.host.trim() ? value.host : jdbc.host,
      port:
        typeof value.port === "number" || typeof value.port === "string"
          ? String(value.port)
          : jdbc.port || defaultPort(dbType),
      database:
        typeof value.database === "string" && value.database.trim()
          ? value.database
          : jdbc.database,
      username: typeof value.username === "string" ? value.username : undefined,
      password: typeof value.password === "string" ? value.password : undefined,
      driverId: normalizeMySqlDriverId(value.driverId),
      properties: parseProperties(value.properties),
      compatibleMode: value.compatibleMode === "oracle" ? "oracle" : "mysql",
      // Never populate sensitive SSH keys from masked backend records.
      privateKey: "",
    };
  } catch {
    if (dbType === "ORACLE") return { jdbcUrl: recordJdbcUrl };
    const jdbc = parseJdbcUrl(dbType, recordJdbcUrl);
    return {
      ...jdbc,
      port: jdbc.port || defaultPort(dbType),
    };
  }
};

export const buildJdbcPreview = (values: FormValues) => {
  const host = values.host.trim();
  const port = values.port.trim();
  const database = values.database.trim();
  const authority = host + (port ? ":" + port : "");
  let jdbcUrl: string;

  switch (normalizeDataSourceType(values.dbType)) {
    case "POSTGRE_SQL":
      jdbcUrl = "jdbc:postgresql://" + authority + "/" + database;
      break;
    case "SQLSERVER":
      jdbcUrl = "jdbc:sqlserver://" + authority + ";databaseName=" + database;
      break;
    case "DB2":
      jdbcUrl = "jdbc:db2://" + authority + "/" + database;
      break;
    case "DAMENG":
      jdbcUrl = "jdbc:dm://" + authority + "/" + database;
      break;
    case "OCEANBASE":
      jdbcUrl = "jdbc:oceanbase://" + authority + "/" + database;
      break;
    case "CLICKHOUSE":
      jdbcUrl = "jdbc:clickhouse://" + authority + "/" + database;
      break;
    case "DORIS":
      jdbcUrl = "jdbc:mysql:loadbalance://" +
        host.split(",").map((candidate) => candidate.trim() + ":" + port).join(",") +
        "/" + database;
      break;
    case "SSH":
      return "ssh://" + authority;
    default:
      jdbcUrl = "jdbc:mysql://" + authority + "/" + database;
  }

  const query = values.properties
    .filter((property) => property.key.trim())
    .map(
      (property) =>
        encodeURIComponent(property.key.trim()) + "=" + encodeURIComponent(property.value),
    )
    .join("&");
  return query ? jdbcUrl + "?" + query : jdbcUrl;
};

export const normalizePropertyKey = (value: string) => value.trim().toLowerCase();
