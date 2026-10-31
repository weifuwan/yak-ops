package io.yak.ops.flow.runtime.partitioning;

import io.yak.ops.core.api.operators.Collector;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.OperationType;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.function.HashFunction;
import io.yak.ops.core.function.HashFunctionProvider;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaUtils;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Broadcasts schema events and sends each data change to a sink-selected partition.
 *
 * <p>Each source subtask announces its current schemas before emitting rows, including after
 * recovery. Schema events update the hash function on the mailbox before any following row.
 * A schema must be known before data can be partitioned; unknown events never disappear silently.
 */
public final class RegularPrePartitionOperator implements OneInputStreamOperator<Event, PartitioningEvent> {

    private final int downstreamParallelism;
    private final HashFunctionProvider<DataChangeEvent> hashFunctionProvider;
    private final Map<TableId, Schema> schemas = new HashMap<>();
    private final Map<TableId, HashFunction<DataChangeEvent>> hashFunctions = new HashMap<>();
    private HashFunction.HashContext hashContext;

    public RegularPrePartitionOperator(
            int downstreamParallelism, HashFunctionProvider<DataChangeEvent> hashFunctionProvider) {
        if (downstreamParallelism <= 0) {
            throw new IllegalArgumentException("Downstream parallelism must be positive");
        }
        this.downstreamParallelism = downstreamParallelism;
        this.hashFunctionProvider = Objects.requireNonNull(hashFunctionProvider, "hashFunctionProvider");
    }

    @Override
    public void setup(RuntimeTaskInfo taskInfo) {
        Objects.requireNonNull(taskInfo, "taskInfo");
        if (hashContext != null) {
            throw new IllegalStateException("Pre-partition operator is already assigned to a subtask");
        }
        hashContext = HashFunction.createContext(taskInfo.subtaskIndex(), downstreamParallelism);
    }

    @Override
    public void processElement(Event element, Collector<PartitioningEvent> output) throws Exception {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(output, "output");
        if (hashContext == null) {
            throw new IllegalStateException("Pre-partition operator has no runtime context");
        }
        if (element instanceof SchemaChangeEvent schemaChange) {
            TableId table = schemaChange.tableId();
            Schema previous = schemas.get(table);
            if (schemaChange instanceof CreateTableEvent create
                    && previous != null
                    && !previous.equals(create.getPhysicalSchema())) {
                throw new IllegalStateException("CREATE_TABLE cannot replace a previously announced schema");
            }
            if (!(schemaChange instanceof CreateTableEvent) && previous == null) {
                throw new IllegalStateException("Schema change precedes the table's CREATE_TABLE event");
            }
            Schema updated = SchemaUtils.applySchemaChangeEvent(previous, schemaChange);
            HashFunction<DataChangeEvent> hashFunction = Objects.requireNonNull(
                    hashFunctionProvider.getHashFunction(table, updated), "HashFunctionProvider returned null");
            schemas.put(table, updated);
            hashFunctions.put(table, hashFunction);
            for (int target = 0; target < downstreamParallelism; target++) {
                output.collect(PartitioningEvent.ofRegular(schemaChange.copy(table), target));
            }
        } else if (element instanceof DataChangeEvent dataChange) {
            HashFunction<DataChangeEvent> hashFunction = hashFunctions.get(dataChange.tableId());
            if (hashFunction == null) {
                throw new IllegalStateException("Data change precedes the table's CREATE_TABLE event");
            }
            int target = partition(hashFunction, dataChange);
            if (dataChange.op() == OperationType.UPDATE && downstreamParallelism > 1) {
                int before = partition(hashFunction, dataChange.withImages(dataChange.before(), dataChange.before()));
                int after = partition(hashFunction, dataChange.withImages(dataChange.after(), dataChange.after()));
                if (before != target || after != target) {
                    throw new UnsupportedOperationException(
                            "Atomic UPDATE images must belong to the same sink partition");
                }
            }
            output.collect(PartitioningEvent.ofRegular(dataChange, target));
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported pipeline event: " + element.getClass().getName());
        }
    }

    private int partition(HashFunction<DataChangeEvent> hashFunction, DataChangeEvent event) {
        return Math.floorMod(hashFunction.hashcode(hashContext, event), downstreamParallelism);
    }
}
