package io.yak.ops.flow.connector.jdbc;

import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.util.Map;

/**
 * JDBC Connector 测试使用的 DataSourceConnection。
 *
 * @param type 数据库类型
 * @param jdbcUrl 测试 JDBC URL
 * @param driverClassName 测试 Driver 类
 * @param username 用户名
 * @param password 密码
 * @author weifuwan
 * @since 2026-09-27
 */
record TestDataSourceConnection(
        String type, String jdbcUrl, String driverClassName, String username, String password)
        implements DataSourceConnection {

    @Override
    public String database() {
        return null;
    }

    @Override
    public String schema() {
        return null;
    }

    @Override
    public Map<String, String> properties() {
        return Map.of();
    }

    @Override
    public String normalizedJson() {
        return "{}";
    }
}
