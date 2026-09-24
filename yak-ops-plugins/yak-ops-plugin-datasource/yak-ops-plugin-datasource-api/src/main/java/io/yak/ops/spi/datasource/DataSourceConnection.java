package io.yak.ops.spi.datasource;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.Map;

/**
 * 插件解析并规范化后的数据源连接参数。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface DataSourceConnection {

    /** 数据库类型。 */
    DataSourceDbType dbType();

    /** 插件规范化后的连接地址。 */
    String jdbcUrl();

    /** JDBC 驱动类；非 JDBC 插件可以为空。 */
    String driverClassName();

    /** 连接用户名。 */
    String username();

    /** 连接密码等敏感凭证。 */
    String password();

    /** 默认数据库名称。 */
    String database();

    /** 默认 Schema 名称。 */
    String schema();

    /** 插件附加连接属性。 */
    Map<String, String> properties();

    /** 可持久化并用于编辑回显的规范化 JSON。 */
    String normalizedJson();
}
