package io.yak.ops.plugin.database.jdbc;

import java.sql.Connection;

/**
 * 抽象 JDBC Connection 的创建动作，供 Catalog 等共享 JDBC 能力复用。
 *
 * <p>实现负责处理 Driver、超时和可选 SSH 隧道，但不得记录连接凭证。</p>
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@FunctionalInterface
public interface JdbcConnectionProvider {

    Connection open(JdbcConnectionProperties connection, int timeoutSeconds) throws Exception;
}
