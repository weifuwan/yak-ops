package io.yak.ops.connector.base.factory;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.TableSchema;

/** Creates a Sink from effective configuration and table metadata. */
public interface TableSinkFactory extends Factory {

    /** Checks write semantics without accessing external metadata. */
    void validate(ReadonlyConfig options, boolean sourceProducesChangelog);

    /** Checks resolved table schemas and primary-key requirements. */
    void validateSchema(
            ReadonlyConfig options, TableSchema source, TableSchema target, boolean sourceProducesChangelog);

    /** Constructs one reusable Sink definition without starting a writer. */
    Sink<TableRecord> createSink(TableSinkFactoryContext context);
}
