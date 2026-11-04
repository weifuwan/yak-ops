package org.apache.flink.cdc.common.sink;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.common.event.DataChangeEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.function.HashFunction;
import org.apache.flink.cdc.common.function.HashFunction.HashContext;
import org.apache.flink.cdc.common.function.HashFunctionProvider;
import org.apache.flink.cdc.common.schema.Schema;

import javax.annotation.Nullable;

/** A {@link HashFunctionProvider} that preserves the upstream subtask distribution. */
@Internal
public class ForwardHashFunctionProvider implements HashFunctionProvider<DataChangeEvent> {

    private static final long serialVersionUID = 1L;

    @Override
    public HashFunction<DataChangeEvent> getHashFunction(@Nullable TableId tableId, Schema schema) {
        return new ForwardHashFunction();
    }

    private static class ForwardHashFunction implements HashFunction<DataChangeEvent> {

        @Override
        public int hashcode(DataChangeEvent event) {
            throw new UnsupportedOperationException(
                    "Forward hash function requires a HashContext.");
        }

        @Override
        public int hashcode(HashContext context, DataChangeEvent event) {
            return context.getSourceSubtaskIndex();
        }
    }
}
