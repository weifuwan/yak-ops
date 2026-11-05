import assert from "node:assert/strict";
import { test } from "node:test";

import {
  fromBackendDataSource,
  normalizeBackendType,
  parseOracleJdbcUrl,
  toBackendDataSource,
  toBackendConnectionTest,
} from "../apps/web/service/datasource/adapter.ts";

const source = (dbType, connectionParams) => ({
  name: "Production",
  remark: "Reporting",
  environment: "DEVELOP",
  dbType,
  connectionParams: {
    username: "root",
    password: "test-pass",
    properties: { connectTimeout: "10000" },
    ...connectionParams,
  },
});

test("MySQL form produces the flat Java DataSourceParamDTO", () => {
  assert.deepEqual(toBackendDataSource(source("MYSQL", {
    host: "127.0.0.1", port: 3306, database: "test", driverId: "AUTO",
  })), {
    type: "MYSQL",
    name: "Production",
    note: "Reporting",
    host: "127.0.0.1",
    port: 3306,
    database: "test",
    userName: "root",
    password: "test-pass",
    other: { connectTimeout: "10000" },
  });
});

test("PostgreSQL enum matches the Java enum, not the legacy frontend value", () => {
  const payload = toBackendDataSource(source("POSTGRE_SQL", {
    host: "localhost", port: 5432, database: "demo",
  }));
  assert.equal(payload.type, "POSTGRESQL");
  assert.equal(normalizeBackendType("POSTGRESQL"), "POSTGRE_SQL");
  assert.equal(normalizeBackendType(1), "POSTGRE_SQL");
});

test("Oracle service name URL preserves its mode", () => {
  const payload = toBackendDataSource(source("ORACLE", {
    jdbcUrl: "jdbc:oracle:thin:@//db.internal:1521/appdb",
  }));
  assert.equal(payload.host, "db.internal");
  assert.equal(payload.port, 1521);
  assert.equal(payload.database, "appdb");
  assert.equal(payload.connectType, "ORACLE_SERVICE_NAME");
});

test("Oracle SID URL preserves its mode", () => {
  const payload = toBackendDataSource(source("ORACLE", {
    jdbcUrl: "jdbc:oracle:thin:@db.internal:1521:ORCL",
  }));
  assert.equal(payload.database, "ORCL");
  assert.equal(payload.connectType, "ORACLE_SID");
  assert.throws(() => parseOracleJdbcUrl("jdbc:oracle:thin:@db.internal"), /Oracle URL/);
  assert.throws(() => parseOracleJdbcUrl("jdbc:oracle:thin:@db:65536:BAD"), /端口/);
});

test("Java detail converts to editable UI values without reusing masked password", () => {
  const record = fromBackendDataSource({
    id: 12, name: "db", type: "MYSQL", host: "127.0.0.1", port: 3306,
    database: "local", userName: "app", password: "******",
    other: { serverTimezone: "UTC" },
  });
  assert.equal(record.id, "12");
  assert.equal(record.dbType, "MYSQL");
  assert.equal(JSON.parse(record.originalJson).password, "");
  assert.equal(JSON.parse(record.originalJson).properties.serverTimezone, "UTC");
});

test("Java paginated entities render JDBC preview without leaking stored password", () => {
  const record = fromBackendDataSource({
    id: 32, name: "db", type: 1,
    connectionParams: JSON.stringify({
      address: "jdbc:postgresql://db.example:5432",
      jdbcUrl: "jdbc:postgresql://db.example:5432/analytics",
      database: "analytics", user: "reader", password: "******",
    }),
  });
  assert.equal(record.dbType, "POSTGRE_SQL");
  assert.equal(record.displayJdbcUrl, "jdbc:postgresql://db.example:5432/analytics");
  assert.equal(JSON.parse(record.originalJson).username, "reader");
});

test("Connection tests use the same validated server DTO as saving", () => {
  const connectionParams = {
    host: "localhost", port: 3306, database: "orders",
    username: "app", password: "pw", properties: {},
  };
  const payload = toBackendConnectionTest("MYSQL", connectionParams);
  assert.equal(payload.name, "connection-test");
  assert.equal(payload.userName, "app");
  assert.equal(payload.password, "pw");
});

test("Masked password is never submitted as a real password", () => {
  const payload = toBackendDataSource(source("MYSQL", {
    host: "localhost", port: 3306, database: "test", password: "******",
  }));
  assert.equal(payload.password, "");
});
