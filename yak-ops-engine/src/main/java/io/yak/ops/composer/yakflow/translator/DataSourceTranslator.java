package io.yak.ops.composer.yakflow.translator;

import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
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
        if (source instanceof MySqlSource mysql && mysql.startupOptions().isStreamOnly() && sourceParallelism != 1) {
            throw new UnsupportedOperationException("Stream-only MySQL CDC requires Source parallelism 1");
        }
        SourceTransformation<TableRecord> input =
                new SourceTransformation<>("data-sync-source", source, TableRecord.class, sourceParallelism);
        input.setUid(new OperatorUidGenerator("data-sync-source").generateUid(definitionId));
        return input;
    }
}
