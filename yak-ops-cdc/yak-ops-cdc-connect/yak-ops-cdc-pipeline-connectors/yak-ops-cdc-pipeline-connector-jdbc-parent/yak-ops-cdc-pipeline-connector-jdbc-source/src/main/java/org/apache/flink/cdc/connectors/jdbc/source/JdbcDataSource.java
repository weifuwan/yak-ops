package org.apache.flink.cdc.connectors.jdbc.source;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.source.DataSource;
import org.apache.flink.cdc.common.source.EventSourceProvider;
import org.apache.flink.cdc.common.source.FlinkSourceProvider;
import org.apache.flink.cdc.common.source.MetadataAccessor;
import org.apache.flink.cdc.connectors.jdbc.catalog.JdbcCatalog;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSourceConfig;

import javax.annotation.Nullable;

import java.util.List;

/** Flink CDC Pipeline wrapper around the bounded JDBC source. */
public final class JdbcDataSource implements DataSource {

    private final JdbcSourceConfig config;

    public JdbcDataSource(JdbcSourceConfig config) {
        this.config = config;
    }

    @Override
    public EventSourceProvider getEventSourceProvider() {
        return FlinkSourceProvider.of(new JdbcSource(config));
    }

    @Override
    public MetadataAccessor getMetadataAccessor() {
        return new JdbcMetadataAccessor(config);
    }

    @Override
    public boolean isParallelMetadataSource() {
        return true;
    }
}

final class JdbcMetadataAccessor implements MetadataAccessor {

    private final JdbcCatalog catalog;

    JdbcMetadataAccessor(JdbcSourceConfig config) {
        this.catalog = new JdbcCatalog(config);
    }

    @Override
    public List<String> listNamespaces() {
        return catalog.listNamespaces();
    }

    @Override
    public List<String> listSchemas(@Nullable String namespace) {
        return catalog.listSchemas(namespace);
    }

    @Override
    public List<TableId> listTables(@Nullable String namespace, @Nullable String schemaName) {
        return catalog.listTables(namespace, schemaName);
    }

    @Override
    public Schema getTableSchema(TableId tableId) {
        return catalog.getTableSchema(tableId);
    }
}
