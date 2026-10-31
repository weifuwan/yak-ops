package io.yak.ops.composer.yakflow.translator;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.source.MetadataAccessor;
import io.yak.ops.flow.runtime.schema.SchemaCoordinatingSource;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * YakFlow adaptation of Flink CDC's DataSourceTranslator: translates a prepared Source
 * into a YakFlow SourceTransformation, retaining the existing stable operator UID.
 */
public final class DataSourceTranslator {

    public SourceTransformation<io.yak.ops.core.event.Event> translate(
            io.yak.ops.core.source.DataSource source, int sourceParallelism, String definitionId) {
        if (source.isParallelMetadataSource()) {
            throw new UnsupportedOperationException(
                    "Regular schema coordination requires a single metadata history per table");
        }
        Map<TableId, Schema> schemas = new LinkedHashMap<>();
        try (MetadataAccessor metadata = source.getMetadataAccessor()) {
            for (TableId table : metadata.listTables(null, null)) {
                if (schemas.putIfAbsent(table, metadata.getTableSchema(table)) != null) {
                    throw new IllegalArgumentException("Source metadata repeats a table");
                }
            }
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot close source metadata accessor", failure);
        }
        Source<Event, ?, ?> coordinated =
                new SchemaCoordinatingSource<>(source.getEventSourceProvider().getSource(), schemas);
        return translate(coordinated, Event.class, sourceParallelism, definitionId);
    }

    public <T> SourceTransformation<T> translate(
            Source<T, ?, ?> source, Class<T> outputType, int sourceParallelism, String definitionId) {
        source.validateParallelism(sourceParallelism);
        SourceTransformation<T> input =
                new SourceTransformation<>("data-sync-source", source, outputType, sourceParallelism);
        input.setUid(new OperatorUidGenerator("data-sync-source").generateUid(definitionId));
        return input;
    }
}
