package io.yak.ops.composer.yakflow.translator;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;

/**
 * YakFlow adaptation of Flink CDC's DataSourceTranslator: translates a prepared Source
 * into a YakFlow SourceTransformation, retaining the existing stable operator UID.
 */
public final class DataSourceTranslator {

    public SourceTransformation<TableRecord> translate(
            Source<TableRecord, ?, ?> source, int sourceParallelism, String definitionId) {
        source.validateParallelism(sourceParallelism);
        SourceTransformation<TableRecord> input =
                new SourceTransformation<>("data-sync-source", source, TableRecord.class, sourceParallelism);
        input.setUid(new OperatorUidGenerator("data-sync-source").generateUid(definitionId));
        return input;
    }
}
