package io.yak.ops.core.source;

/** A multi-table event source and its independently readable metadata. */
public interface DataSource {
    EventSourceProvider getEventSourceProvider();

    MetadataAccessor getMetadataAccessor();

    /** Whether one table can have independent, concurrent metadata histories. */
    default boolean isParallelMetadataSource() {
        return false;
    }
}
