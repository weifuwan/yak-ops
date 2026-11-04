package org.apache.flink.cdc.connectors.mysql.source.connection;

import java.io.Serializable;
import java.util.Objects;

/** The connection pool identifier. */
public class ConnectionPoolId implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String host;
    private final int port;
    private final String username;

    public ConnectionPoolId(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
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
                && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, username);
    }

    @Override
    public String toString() {
        return username + '@' + host + ':' + port;
    }
}
