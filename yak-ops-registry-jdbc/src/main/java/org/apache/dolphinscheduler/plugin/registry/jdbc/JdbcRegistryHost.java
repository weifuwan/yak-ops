package org.apache.dolphinscheduler.plugin.registry.jdbc;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Yak Ops compatibility adapter for the only NetUtils method used by JDBC
 * Registry. Keeps the upstream registry independent of Kubernetes utilities.
 */
public final class JdbcRegistryHost {
    private JdbcRegistryHost() {}

    public static String getHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException failure) {
            String host = System.getenv("HOSTNAME");
            if (host != null && !host.isBlank()) {
                return host;
            }
            return "localhost";
        }
    }
}
