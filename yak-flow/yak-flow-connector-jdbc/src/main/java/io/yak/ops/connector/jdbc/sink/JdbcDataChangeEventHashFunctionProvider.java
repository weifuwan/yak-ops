package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.function.HashFunction;
import io.yak.ops.core.function.HashFunctionProvider;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.sink.DefaultDataChangeEventHashFunctionProvider;
import java.util.Objects;

/**
 * Keeps each table's JDBC mutations on one writer while distributing append-only rows by key.
 *
 * <p>A JDBC UPDATE can delete an old write key and insert a new one in the same transaction.
 * Both keys, earlier mutations and later key reuse must share a writer to retain input order.
 * Changelog parallelism therefore distributes tables, not rows within a table. This preserves
 * the indivisible Event and its checkpoint identity without coordinating JDBC transactions
 * across writers. Append-only sources retain the regular primary-key partitioning contract.
 */
public final class JdbcDataChangeEventHashFunctionProvider implements HashFunctionProvider<DataChangeEvent> {
    private static final long serialVersionUID = 1L;

    private final boolean sourceProducesChangelog;

    public JdbcDataChangeEventHashFunctionProvider(boolean sourceProducesChangelog) {
        this.sourceProducesChangelog = sourceProducesChangelog;
    }

    @Override
    public HashFunction<DataChangeEvent> getHashFunction(TableId tableId, Schema schema) {
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(schema, "schema");
        if (!sourceProducesChangelog) {
            return new DefaultDataChangeEventHashFunctionProvider().getHashFunction(tableId, schema);
        }
        int tableHash = Objects.hash(tableId.catalog(), tableId.schema(), tableId.table());
        return event -> {
            if (!tableId.equals(Objects.requireNonNull(event, "event").tableId())) {
                throw new IllegalArgumentException("Data event belongs to another table");
            }
            return tableHash;
        };
    }
}
