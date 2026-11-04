package org.apache.flink.cdc.common.sink;

import org.apache.flink.cdc.common.event.DataChangeEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.function.HashFunction;
import org.apache.flink.cdc.common.function.HashFunctionProvider;
import org.apache.flink.cdc.common.schema.Schema;

import javax.annotation.Nullable;

/**
 * A {@link HashFunctionProvider} implementation that hashes events based solely on {@link TableId}.
 *
 * <p>This provider ensures all events from the same table are routed to the same downstream
 * subtask, regardless of their primary key values or record payload. The hash is computed once when
 * the HashFunction is created and cached for all subsequent events.
 */
public class TableIdHashFunctionProvider implements HashFunctionProvider<DataChangeEvent> {

    private static final long serialVersionUID = 1L;

    @Override
    public HashFunction<DataChangeEvent> getHashFunction(@Nullable TableId tableId, Schema schema) {
        return new TableIdHashFunction(tableId);
    }

    /** A {@link HashFunction} that computes hash based solely on TableId, cached at creation. */
    static class TableIdHashFunction implements HashFunction<DataChangeEvent> {

        private final int cachedHash;

        TableIdHashFunction(TableId tableId) {
            this.cachedHash = (tableId.hashCode() * 31) & 0x7FFFFFFF;
        }

        @Override
        public int hashcode(DataChangeEvent event) {
            return cachedHash;
        }
    }
}
