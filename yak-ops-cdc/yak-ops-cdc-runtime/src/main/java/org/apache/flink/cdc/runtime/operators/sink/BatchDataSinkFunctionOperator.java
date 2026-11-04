package org.apache.flink.cdc.runtime.operators.sink;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.cdc.runtime.operators.sink.exception.SinkWrapperException;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.operators.StreamSink;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;

/**
 * An operator that processes records to be written into a {@link SinkFunction} in batch mode.
 *
 * <p>The operator is a proxy of {@link StreamSink} in Flink.
 *
 * <p>The operator is always part of a sink pipeline and is the first operator.
 */
@Internal
public class BatchDataSinkFunctionOperator extends StreamSink<Event> {

    public BatchDataSinkFunctionOperator(SinkFunction<Event> userFunction) {
        super(userFunction);
    }

    @Override
    public void processElement(StreamRecord<Event> element) throws Exception {
        Event event = element.getValue();

        try {
            super.processElement(element);
        } catch (Exception e) {
            throw new SinkWrapperException(event, e);
        }
    }
}
