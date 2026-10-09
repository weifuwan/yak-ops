package io.yak.ops.connector.jdbc.source.reader;

import io.yak.ops.core.data.TableRecord;
import java.util.Objects;

/** A fetched database row and its optional ordered primary-key position. */
public record JdbcRecordAndPosition(TableRecord record, Long lastKey) {

    public JdbcRecordAndPosition {
        Objects.requireNonNull(record, "record");
    }
}
