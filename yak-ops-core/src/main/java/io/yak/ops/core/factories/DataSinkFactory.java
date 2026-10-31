package io.yak.ops.core.factories;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.sink.DataSink;
import io.yak.ops.core.types.TableSchema;

/** Creates a DataSink from independently selected connector configuration. */
public interface DataSinkFactory extends Factory {
    DataSink createDataSink(Context context);

    default boolean supportsSchemaChanges() {
        return false;
    }

    default boolean supportsRetract() {
        return false;
    }

    void validate(ReadonlyConfig options, boolean sourceProducesChangelog);

    void validateSchema(
            ReadonlyConfig options, TableSchema source, TableSchema target, boolean sourceProducesChangelog);

    default MetadataApplier createMetadataApplier(ReadonlyConfig options) {
        throw new UnsupportedOperationException("Sink does not support metadata changes");
    }
}
