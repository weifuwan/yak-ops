package org.apache.flink.cdc.connectors.jdbc.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable configuration for the bounded JDBC source. */
public final class JdbcSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String url;
    private final String driverName;
    private final String username;
    private final String password;
    private final List<String> tables;
    private final int fetchSize;
    private final int splitSize;
    private final int parallelism;

    public JdbcSourceConfig(
            String url,
            String driverName,
            String username,
            String password,
            List<String> tables,
            int fetchSize,
            int splitSize,
            int parallelism) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("JDBC url must not be empty");
        }
        if (driverName == null || driverName.trim().isEmpty()) {
            throw new IllegalArgumentException("JDBC driver-name must not be empty");
        }
        if (tables == null || tables.isEmpty()) {
            throw new IllegalArgumentException("JDBC source requires at least one table");
        }
        if (fetchSize < 1 || fetchSize > 100000) {
            throw new IllegalArgumentException("scan.fetch-size must be between 1 and 100000");
        }
        if (splitSize < 1 || splitSize > 10000000) {
            throw new IllegalArgumentException("scan.split-size must be between 1 and 10000000");
        }
        if (parallelism < 1 || parallelism > 128) {
            throw new IllegalArgumentException("pipeline.parallelism must be between 1 and 128");
        }
        this.url = url.trim();
        this.driverName = driverName.trim();
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.tables = Collections.unmodifiableList(new ArrayList<>(tables));
        this.fetchSize = fetchSize;
        this.splitSize = splitSize;
        this.parallelism = parallelism;
    }

    public String getUrl() {
        return url;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public List<String> getTables() {
        return tables;
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public int getSplitSize() {
        return splitSize;
    }

    public int getParallelism() {
        return parallelism;
    }
}
