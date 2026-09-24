package io.yak.ops.plugin.database.elasticsearch;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourceConnection;
import java.util.List;
import java.util.Map;

/** SDK-free Elasticsearch connection model owned by the Yak Ops control-plane plugin. */
final class ElasticsearchConnection implements DataSourceConnection {

    static final String VIRTUAL_DATABASE = "elasticsearch";

    private final DataSourceDbType dbType;
    private final List<String> hosts;
    private final String username;
    private final String password;
    private final String normalizedJson;

    ElasticsearchConnection(
            DataSourceDbType dbType, List<String> hosts, String username, String password, String normalizedJson) {
        this.dbType = dbType;
        this.hosts = List.copyOf(hosts);
        this.username = username;
        this.password = password;
        this.normalizedJson = normalizedJson;
    }

    List<String> hosts() {
        return hosts;
    }

    String primaryHost() {
        return hosts.get(0);
    }

    @Override
    public DataSourceDbType dbType() {
        return dbType;
    }

    /** Historical SPI field; for HTTP-native sources it carries the primary endpoint. */
    @Override
    public String jdbcUrl() {
        return primaryHost();
    }

    @Override
    public String driverClassName() {
        return null;
    }

    @Override
    public String username() {
        return username;
    }

    @Override
    public String password() {
        return password;
    }

    @Override
    public String database() {
        return VIRTUAL_DATABASE;
    }

    @Override
    public String schema() {
        return null;
    }

    @Override
    public Map<String, String> properties() {
        return Map.of("hosts", String.join(",", hosts));
    }

    @Override
    public String normalizedJson() {
        return normalizedJson;
    }
}
