package org.apache.flink.cdc.debezium.utils;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.table.catalog.Column;
import org.apache.flink.table.catalog.ResolvedSchema;

import java.util.stream.Collectors;

/** Utilities to {@link ResolvedSchema}. */
@Internal
public class ResolvedSchemaUtils {
    private ResolvedSchemaUtils() {}

    /** Return {@link ResolvedSchema} which consists of all physical columns. */
    public static ResolvedSchema getPhysicalSchema(ResolvedSchema resolvedSchema) {
        return new ResolvedSchema(
                resolvedSchema.getColumns().stream()
                        .filter(Column::isPhysical)
                        .collect(Collectors.toList()),
                resolvedSchema.getWatermarkSpecs(),
                resolvedSchema.getPrimaryKey().orElse(null));
    }
}
