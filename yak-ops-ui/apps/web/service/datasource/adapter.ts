import type {
  DataSourceConnectionParams,
  DataSourceRecord,
  DataSourceSavePayload,
} from "./types";

/**
 * The UI model is deliberately independent of DolphinScheduler's datasource DTO.
 * Keep the Java API as the single owner of JDBC URL generation and connectivity.
 */
export interface BackendDataSourceParam {
  type: string;
  name: string;
  note: string;
  host: string;
  port: number;
  database: string;
  userName: string;
  password: string;
  other: Record<string, string>;
  connectType?: "ORACLE_SID" | "ORACLE_SERVICE_NAME";
  compatibleMode?: "mysql" | "oracle";
  privateKey?: string;
}

export interface BackendDataSourceRecord {
  id?: number | string;
  name?: string;
  note?: string;
  type?: string | number | { name?: string; code?: number };
  host?: string;
  port?: number;
  database?: string;
  userName?: string;
  password?: string;
  other?: Record<string, string>;
  connectType?: string;
  compatibleMode?: string;
  privateKey?: string;
  connectionParams?: string;
  createTime?: string;
  updateTime?: string;
}

const CODE_TYPES: Record<number, string> = {
  0: "MYSQL",
  1: "POSTGRE_SQL",
  4: "CLICKHOUSE",
  5: "ORACLE",
  6: "SQLSERVER",
  7: "DB2",
  13: "STARROCKS",
  15: "DAMENG",
  16: "OCEANBASE",
  17: "SSH",
  23: "DORIS",
};
const SECRET_QUERY_PARAM = /([?&](?:password|pwd|token|secret|accessKey)=)[^&]*/gi;

export const normalizeBackendType = (type: unknown): string => {
  if (typeof type === "number") return CODE_TYPES[type] ?? String(type);
  if (typeof type === "object" && type) {
    const entry = type as { name?: string; code?: number };
    return normalizeBackendType(entry.name ?? entry.code);
  }
  const value = String(type ?? "").trim().toUpperCase();
  if (value === "POSTGRESQL" || value === "POSTGRES") return "POSTGRE_SQL";
  return value;
};

export const toBackendType = (type: string) =>
  normalizeBackendType(type) === "POSTGRE_SQL" ? "POSTGRESQL" : normalizeBackendType(type);

export function parseOracleJdbcUrl(url: string): {
  host: string;
  port: number;
  database: string;
  connectType: "ORACLE_SID" | "ORACLE_SERVICE_NAME";
} {
  const value = url.trim();
  const service = /^jdbc:oracle:thin:@\/\/(\[[^\]]+\]|[^:/\s]+):(\d+)\/([^?/\s]+)$/i.exec(value);
  const sid = /^jdbc:oracle:thin:@(\[[^\]]+\]|[^:/\s]+):(\d+):([^?/\s]+)$/i.exec(value);
  const match = service ?? sid;
  if (!match) {
    throw new Error("Oracle URL 必须采用 jdbc:oracle:thin:@//host:port/service 或 jdbc:oracle:thin:@host:port:SID");
  }
  const port = Number(match[2]);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("Oracle 端口不合法");
  }
  return {
    host: match[1],
    port,
    database: match[3],
    connectType: service ? "ORACLE_SERVICE_NAME" : "ORACLE_SID",
  };
}

export function toBackendDataSource(
  source: DataSourceSavePayload,
): BackendDataSourceParam {
  const params: DataSourceConnectionParams = source.connectionParams;
  const type = toBackendType(source.dbType);
  const endpoint =
    type === "ORACLE"
      ? parseOracleJdbcUrl(params.jdbcUrl || "")
      : {
          host: params.host?.trim() ?? "",
          port: Number(params.port),
          database: params.database?.trim() ?? "",
        };
  if (!endpoint.host || !Number.isInteger(endpoint.port) || endpoint.port < 1 || endpoint.port > 65535 || (type !== "SSH" && !endpoint.database)) {
    throw new Error("请填写完整且有效的数据库地址、端口和数据库名称");
  }
  return {
    type,
    name: source.name.trim(),
    note: source.remark?.trim() ?? "",
    host: endpoint.host,
    port: endpoint.port,
    database: endpoint.database,
    userName: params.username.trim(),
    // Empty password on UPDATE intentionally retains the old server-side password.
    password: params.password === "******" ? "" : params.password,
    other: { ...params.properties },
    ...(type === "ORACLE"
      ? { connectType: (endpoint as ReturnType<typeof parseOracleJdbcUrl>).connectType }
      : {}),
    ...(type === "OCEANBASE" ? { compatibleMode: params.compatibleMode || "mysql" } : {}),
    ...(type === "SSH" ? { privateKey: params.privateKey?.trim() || "" } : {}),
  };
}

const parseConnectionParams = (value: unknown): Record<string, unknown> => {
  if (typeof value !== "string" || !value.trim()) return {};
  try {
    const parsed: unknown = JSON.parse(value);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : {};
  } catch {
    return {};
  }
};

const stringField = (value: unknown) => (typeof value === "string" ? value : "");
const numberField = (value: unknown) =>
  typeof value === "number" && Number.isFinite(value) ? value : undefined;
const mapField = (value: unknown): Record<string, string> => {
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([key, v]) => [key, String(v ?? "")]),
  );
};

function urlFromEndpoint(
  type: string,
  host: string,
  port: number | undefined,
  database: string,
  connectType: string,
): string {
  if (!host || !port || !database) return "";
  if (type === "ORACLE") {
    return connectType === "ORACLE_SID"
      ? "jdbc:oracle:thin:@" + host + ":" + port + ":" + database
      : "jdbc:oracle:thin:@//" + host + ":" + port + "/" + database;
  }
  if (type === "SSH") return "ssh://" + host + ":" + port;
  if (type === "SQLSERVER") return "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + database;
  if (type === "DORIS") {
    return "jdbc:mysql:loadbalance://" +
      host.split(",").map((candidate) => candidate.trim() + ":" + port).join(",") + "/" + database;
  }
  const protocol: Record<string, string> = {
    POSTGRE_SQL: "postgresql",
    DB2: "db2",
    CLICKHOUSE: "clickhouse",
    OCEANBASE: "oceanbase",
    DAMENG: "dm",
    MYSQL: "mysql",
    STARROCKS: "mysql",
  };
  return "jdbc:" + (protocol[type] || "mysql") + "://" + host + ":" + port + "/" + database;
}

export function fromBackendDataSource(raw: BackendDataSourceRecord): DataSourceRecord {
  const type = normalizeBackendType(raw.type);
  const stored = parseConnectionParams(raw.connectionParams);
  const rawUrl = stringField(stored.jdbcUrl);
  const address = stringField(stored.address);
  const addressMatch = /\/\/(\[[^\]]+\]|[^:/?]+):(\d+)/.exec(address || rawUrl);
  const host = raw.host || stringField(stored.host) || addressMatch?.[1] || "";
  const port = raw.port ?? numberField(stored.port) ??
    (addressMatch ? Number(addressMatch[2]) : undefined);
  const database = raw.database || stringField(stored.database);
  const username = raw.userName || stringField(stored.user);
  const connectType = raw.connectType || stringField(stored.connectType) || "ORACLE_SERVICE_NAME";
  const compatibleMode = raw.compatibleMode || stringField(stored.compatibleMode) || "mysql";
  const properties = mapField(raw.other || stored.other);
  const jdbcUrl = rawUrl || urlFromEndpoint(type, host, port, database, connectType);
  // Never round-trip a masked password back to an update/test request.
  const safeEdit = {
    host,
    port,
    database,
    username,
    password: "",
    properties,
    connectType,
    compatibleMode: compatibleMode === "oracle" ? "oracle" : "mysql",
    privateKey: "",
    jdbcUrl: type === "ORACLE" ? urlFromEndpoint(type, host, port, database, connectType) || jdbcUrl : jdbcUrl,
  };
  const displayUrl = (jdbcUrl || safeEdit.jdbcUrl).replace(SECRET_QUERY_PARAM, "$1******");
  return {
    id: raw.id === undefined ? undefined : String(raw.id),
    name: raw.name,
    dbType: type,
    database,
    jdbcUrl: displayUrl,
    displayJdbcUrl: displayUrl,
    remark: raw.note || "",
    connStatus: "UNKNOWN",
    originalJson: JSON.stringify(safeEdit),
    createTime: raw.createTime,
    updateTime: raw.updateTime,
  };
}

export function toBackendConnectionTest(dbType: string, params: DataSourceConnectionParams) {
  return toBackendDataSource({
    name: "connection-test",
    dbType,
    environment: "DEVELOP",
    connectionParams: params,
  });
}
