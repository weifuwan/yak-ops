package org.apache.flink.cdc.runtime.operators.transform.converter;

import org.apache.flink.cdc.common.event.DataChangeEvent;

import java.util.Optional;

import static org.apache.flink.cdc.common.event.OperationType.DELETE;

/** This {@link PostTransformConverter} convert delete events to insert events. */
public class SoftDeleteConverter implements PostTransformConverter {

    @Override
    public Optional<DataChangeEvent> convert(DataChangeEvent dataChangeEvent) {
        if (DELETE.equals(dataChangeEvent.op())) {
            return Optional.of(
                    DataChangeEvent.insertEvent(
                            dataChangeEvent.tableId(),
                            dataChangeEvent.before(),
                            dataChangeEvent.meta()));
        }
        return Optional.of(dataChangeEvent);
    }
}
