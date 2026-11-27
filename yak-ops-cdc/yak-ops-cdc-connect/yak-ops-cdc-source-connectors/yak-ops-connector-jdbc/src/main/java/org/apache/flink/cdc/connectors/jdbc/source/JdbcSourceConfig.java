package org.apache.flink.cdc.connectors.jdbc.source;

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
        if (fetchSize < 1 || splitSize < 1 || parallelism < 1) {
            throw new IllegalArgumentException(
                    "scan.fetch-size, scan.split-size and pipeline.parallelism must be positive");
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

    public String getUrl() { return url; }
    public String getDriverName() { return driverName; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public List<String> getTables() { return tables; }
    public int getFetchSize() { return fetchSize; }
    public int getSplitSize() { return splitSize; }
    public int getParallelism() { return parallelism; }
}
