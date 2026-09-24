package io.yak.ops.business.datasource.plugin;

import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.DataSourceConnection;

/**
 * 提供数据源插件配置、连接解析、连通性检测和 Catalog 创建等稳定业务能力。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface DataSourcePluginBusiness {

    DataSourcePluginConfigVO queryPluginConfig(String pluginType);

    boolean checkPluginAvailable(String pluginType);

    DataSourceConnection parseConnection(DataSourceDbType dbType, String connectionJson);

    DataSourceConnection mergeStoredSecrets(DataSourceDbType dbType, String submittedJson, String storedJson);

    void testConnection(DataSourceDbType dbType, String connectionJson, int timeoutSeconds);

    DataSourceCatalog createCatalog(DataSourceDbType dbType, String connectionJson, int timeoutSeconds);

    String maskConnectionJson(DataSourceDbType dbType, String connectionJson);

    String maskSensitiveText(String value);

    DataSourceDbType resolveConnectionType(String connectionJson);
}
