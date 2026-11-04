package org.apache.flink.cdc.common.source.discover;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.configuration.Configuration;
import org.apache.flink.cdc.common.event.TableId;

/** Table-specific SPI factory that creates a {@link TableDiscoverer}. */
@PublicEvolving
public interface TableDiscovererFactory extends ObjectIdDiscovererFactory<TableId> {

    /** Compatibility alias for the old single identifier, now meaning storage-side type. */
    @Override
    default Class<TableId> objectIdClass() {
        return TableId.class;
    }

    /** Creates a new uninitialized {@link TableDiscoverer}. */
    @Override
    TableDiscoverer createDiscoverer();

    /** Creates a discoverer context with the given configuration and class loader. */
    static ObjectIdDiscoverer.Context createContext(
            Configuration configuration, ClassLoader classLoader) {
        return ObjectIdDiscovererFactory.createContext(configuration, classLoader);
    }

    /** Discovers a table discoverer factory via SPI and delegates discoverer creation to it. */
    static TableDiscoverer createDiscoverer(String type, ClassLoader classLoader) {
        return (TableDiscoverer)
                ObjectIdDiscovererFactory.createDiscoverer(type, TableId.class, classLoader);
    }
}
