package io.yak.ops.spi.datasource;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.Map;

/** 插件解析后的标准数据源连接参数。 */
public interface DataSourceConnection {

    DataSourceDbType dbType();

    String jdbcUrl();

    String driverClassName();

    String username();

    String password();

    String database();

    String schema();

    Map<String, String> properties();

    /** 可持久化并用于编辑回显的规范化 JSON。 */
    String normalizedJson();
}
