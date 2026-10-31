package io.yak.ops.core.factories;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.source.DataSource;
import io.yak.ops.core.types.TableSchema;

/** Creates a DataSource after validating its declared connector contract. */
public interface DataSourceFactory extends Factory {
    DataSource createDataSource(Context context);

    Boundedness boundedness(ReadonlyConfig options);

    boolean producesChangelog();

    /** Returns the mutation capability of this configured source, including its startup mode. */
    default boolean producesChangelog(ReadonlyConfig options) {
        return producesChangelog();
    }

    default boolean supportsSchemaMetadata() {
        return false;
    }

    default boolean supportsIncrementalBatch() {
        return false;
    }

    /** Adds connector-owned attempt settings without depending on platform task types. */
    default ReadonlyConfig prepareExecutionOptions(ReadonlyConfig options, String executionId) {
        return options;
    }

    /** Exclusive runtime resources required by this connector for the execution. */
    default java.util.Map<String, String> exclusiveResources(String executionId) {
        return java.util.Map.of();
    }

    default void validateSchema(TableSchema schema) {}

    default void validateSchema(ReadonlyConfig options, TableSchema schema) {
        validateSchema(schema);
    }
}
