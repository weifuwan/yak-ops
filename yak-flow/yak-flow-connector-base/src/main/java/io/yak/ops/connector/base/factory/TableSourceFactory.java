package io.yak.ops.connector.base.factory;

import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;

/** Creates a Source from effective configuration and table metadata. */
public interface TableSourceFactory extends Factory {

    /** Declares whether this Source is bounded. */
    Boundedness boundedness();

    /** Indicates whether the Source emits update and delete events. */
    boolean producesChangelog();

    /** Checks whether the physical datasource type is supported. */
    default void validateDatasource(String datasourceType) {}

    /** Constructs one reusable Source definition without starting its readers. */
    Source<TableRecord, ?, ?> createSource(TableSourceFactoryContext context);

    /**
     * Constructs one physical Source for a nonempty ordered collection of resolved tables.
     *
     * @param contexts effective connection and table inputs for the shared execution
     * @return reusable Source definition; unsupported multi-table factories reject the request
     */
    default Source<TableRecord, ?, ?> createSource(java.util.List<TableSourceFactoryContext> contexts) {
        if (contexts.size() != 1) throw new UnsupportedOperationException("Factory does not support multiple tables");
        return createSource(contexts.getFirst());
    }
}
