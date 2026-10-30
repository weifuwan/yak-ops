package io.yak.ops.composer.yakflow.translator;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;

/** YakFlow adaptation of Flink CDC's DataSinkTranslator for the supported one-to-one routes. */
public final class DataSinkTranslator {

    public SinkTransformation<TableRecord> translate(
            SourceTransformation<TableRecord> input, Sink<TableRecord> sink, int sinkParallelism, String definitionId) {
        SinkTransformation<TableRecord> output =
                new SinkTransformation<>(input, "data-sync-sink", sink, sinkParallelism);
        output.setUid(new OperatorUidGenerator("data-sync-sink").generateUid(definitionId));
        return output;
    }
}
