package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.core.data.TableRecord;
import java.util.List;
import java.util.Objects;

/**
 * One Debezium change event with its indivisible before/after output and resume position.
 *
 * <p>UPDATE_BEFORE and UPDATE_AFTER remain adjacent inside a single mailbox emission,
 * so a checkpoint barrier cannot be inserted between the two rows.
 */
public record BinlogEvent(List<TableRecord> records, BinlogOffset offset) {

    public BinlogEvent {
        records = List.copyOf(Objects.requireNonNull(records, "records"));
        Objects.requireNonNull(offset, "offset");
    }
}
