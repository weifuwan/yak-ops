package io.yak.ops.plugin.database.jdbc;

import java.sql.Connection;

/** JDBC Catalog / SQL Executor 共用的连接创建入口。 */
@FunctionalInterface
public interface JdbcConnectionProvider {

    Connection open(JdbcConnectionProperties connection, int timeoutSeconds) throws Exception;
}
