package io.yak.ops.plugin.database.mongodb;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourceConnection;
import java.util.Map;

/** MongoDB connection model used by the Yak Ops control-plane datasource plugin. */
final class MongoConnection implements DataSourceConnection {

    private final String uri;
    private final String displayUrl;
    private final String database;
    private final String username;
    private final String password;
    private final String normalizedJson;

    MongoConnection(
            String uri, String displayUrl, String database, String username, String password, String normalizedJson) {
        this.uri = uri;
        this.displayUrl = displayUrl;
        this.database = database;
        this.username = username;
        this.password = password;
        this.normalizedJson = normalizedJson;
    }

    String uri() {
        return uri;
    }

    @Override
    public DataSourceDbType dbType() {
        return DataSourceDbType.MONGODB;
    }

    /** Historical SPI field; keep it credential-free because Yak Ops surfaces it as connection URL. */
    @Override
    public String jdbcUrl() {
        return displayUrl;
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
        return database;
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
        return normalizedJson;
    }
}
