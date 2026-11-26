package io.yak.ops.modeling.ddl.session;

/** Connection descriptor passed from an application module to a DDL session factory. */
public class DdlConnectionInfo {

    private final String databaseType;
    private final String connectionParams;

    public DdlConnectionInfo(String databaseType, String connectionParams) {
        this.databaseType = databaseType;
        this.connectionParams = connectionParams;
    }

    public String getDatabaseType() { return databaseType; }
    public String getConnectionParams() { return connectionParams; }
}
