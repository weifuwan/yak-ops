package org.apache.flink.cdc.common.source.discover;

/**
 * {@link TableDiscovererFactory} for {@link JdbcTableDiscoverer} with factory type {@code jdbc}.
 */
public class JdbcTableDiscovererFactory implements TableDiscovererFactory {

    public static final String TYPE = "jdbc";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public TableDiscoverer createDiscoverer() {
        return new JdbcTableDiscoverer();
    }
}
