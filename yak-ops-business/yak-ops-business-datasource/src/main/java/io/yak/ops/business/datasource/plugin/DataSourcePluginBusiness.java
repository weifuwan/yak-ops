package io.yak.ops.business.datasource.plugin;

import io.yak.ops.common.bean.vo.datasource.plugin.DataSourcePluginConfigVO;
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

    String resolvePluginType(String pluginType);

    DataSourceConnection parseConnection(String pluginType, String connectionJson);

    DataSourceConnection mergeStoredSecrets(String pluginType, String submittedJson, String storedJson);

    void testConnection(String pluginType, String connectionJson, int timeoutSeconds);

    DataSourceCatalog createCatalog(String pluginType, String connectionJson, int timeoutSeconds);

    String maskConnectionJson(String pluginType, String connectionJson);

    String maskSensitiveText(String value);

    String resolveConnectionType(String connectionJson);
}
