package io.yak.ops.composer.yakflow.translator;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;

/** Translates any upstream YakFlow transformation into a terminal Sink node. */
public final class DataSinkTranslator {

    public SinkTransformation<TableRecord> translate(
            Transformation<TableRecord> input, Sink<TableRecord> sink, int sinkParallelism, String definitionId) {
        SinkTransformation<TableRecord> output =
                new SinkTransformation<>(input, "data-sync-sink", sink, sinkParallelism);
        output.setUid(new OperatorUidGenerator("data-sync-sink").generateUid(definitionId));
        return output;
    }
}
