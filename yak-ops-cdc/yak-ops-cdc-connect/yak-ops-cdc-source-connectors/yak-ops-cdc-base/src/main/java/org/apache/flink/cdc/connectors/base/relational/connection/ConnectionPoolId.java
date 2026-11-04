package org.apache.flink.cdc.connectors.base.relational.connection;

import javax.annotation.Nullable;

import java.io.Serializable;
import java.util.Objects;

/** The connection pool identifier. */
public class ConnectionPoolId implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String host;
    private final int port;
    private final String username;

    @Nullable private final String database;

    /** the identifier of a data source pool factory is its class name. */
    private final String dataSourcePoolFactoryIdentifier;

    public ConnectionPoolId(
            String host,
            int port,
            String username,
            @Nullable String database,
            String dataSourcePoolFactoryIdentifier) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.database = database;
        this.dataSourcePoolFactoryIdentifier = dataSourcePoolFactoryIdentifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConnectionPoolId)) {
            return false;
        }
        ConnectionPoolId that = (ConnectionPoolId) o;
        return Objects.equals(host, that.host)
                && Objects.equals(port, that.port)
                && Objects.equals(username, that.username)
                && Objects.equals(database, that.database)
                && Objects.equals(
                        dataSourcePoolFactoryIdentifier, that.dataSourcePoolFactoryIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, username, database, dataSourcePoolFactoryIdentifier);
    }

    @Override
    public String toString() {
        return username
                + '@'
                + host
                + ':'
                + port
                + ", database="
                + database
                + ", dataSourcePoolFactoryIdentifier="
                + dataSourcePoolFactoryIdentifier;
    }

    public String getDataSourcePoolFactoryIdentifier() {
        return dataSourcePoolFactoryIdentifier;
    }
}
