package org.apache.flink.cdc.connectors.mysql.source.offset;

import java.util.HashMap;
import java.util.Map;

import static org.apache.flink.cdc.connectors.mysql.source.offset.BinlogOffsetKind.EARLIEST;
import static org.apache.flink.cdc.connectors.mysql.source.offset.BinlogOffsetKind.NON_STOPPING;
import static org.apache.flink.util.Preconditions.checkArgument;

/** Builder for {@link BinlogOffset}. */
public class BinlogOffsetBuilder {
    private final Map<String, String> offsetMap = new HashMap<>();

    public BinlogOffsetBuilder() {
        // Initialize default values
        offsetMap.put(BinlogOffset.EVENTS_TO_SKIP_OFFSET_KEY, "0");
        offsetMap.put(BinlogOffset.ROWS_TO_SKIP_OFFSET_KEY, "0");
        offsetMap.put(BinlogOffset.TIMESTAMP_KEY, "0");
        offsetMap.put(BinlogOffset.OFFSET_KIND_KEY, String.valueOf(BinlogOffsetKind.SPECIFIC));
    }

    public BinlogOffsetBuilder setBinlogFilePosition(String filename, long position) {
        offsetMap.put(BinlogOffset.BINLOG_FILENAME_OFFSET_KEY, filename);
        offsetMap.put(BinlogOffset.BINLOG_POSITION_OFFSET_KEY, String.valueOf(position));
        return this;
    }

    public BinlogOffsetBuilder setSkipEvents(long skipEvents) {
        offsetMap.put(BinlogOffset.EVENTS_TO_SKIP_OFFSET_KEY, String.valueOf(skipEvents));
        return this;
    }

    public BinlogOffsetBuilder setSkipRows(long skipRows) {
        offsetMap.put(BinlogOffset.ROWS_TO_SKIP_OFFSET_KEY, String.valueOf(skipRows));
        return this;
    }

    public BinlogOffsetBuilder setTimestampSec(long timestampSec) {
        offsetMap.put(BinlogOffset.TIMESTAMP_KEY, String.valueOf(timestampSec));
        return this;
    }

    public BinlogOffsetBuilder setGtidSet(String gtidSet) {
        offsetMap.put(BinlogOffset.GTID_SET_KEY, gtidSet);
        return this;
    }

    public BinlogOffsetBuilder setServerId(long serverId) {
        offsetMap.put(BinlogOffset.SERVER_ID_KEY, String.valueOf(serverId));
        return this;
    }

    public BinlogOffsetBuilder setOffsetKind(BinlogOffsetKind offsetKind) {
        offsetMap.put(BinlogOffset.OFFSET_KIND_KEY, String.valueOf(offsetKind));
        if (offsetKind == EARLIEST) {
            setBinlogFilePosition("", 0);
        }
        if (offsetKind == NON_STOPPING) {
            setBinlogFilePosition("", Long.MIN_VALUE);
        }
        return this;
    }

    public BinlogOffsetBuilder setOffsetMap(Map<String, String> offsetMap) {
        this.offsetMap.putAll(offsetMap);
        return this;
    }

    public BinlogOffset build() {
        sanityCheck();
        return new BinlogOffset(offsetMap);
    }

    private void sanityCheck() {
        checkArgument(
                offsetMap.containsKey(BinlogOffset.OFFSET_KIND_KEY),
                "Binlog offset kind is required");
        BinlogOffsetKind offsetKind =
                BinlogOffsetKind.valueOf(offsetMap.get(BinlogOffset.OFFSET_KIND_KEY));
        switch (offsetKind) {
            case SPECIFIC:
                checkArgument(
                        offsetMap.containsKey(BinlogOffset.BINLOG_FILENAME_OFFSET_KEY)
                                || offsetMap.containsKey(BinlogOffset.BINLOG_POSITION_OFFSET_KEY)
                                || offsetMap.containsKey(BinlogOffset.GTID_SET_KEY),
                        "Either binlog file / position or GTID set is required if offset kind is SPECIFIC");
                break;
            case TIMESTAMP:
                checkArgument(offsetMap.containsKey(BinlogOffset.TIMESTAMP_KEY));
                break;
            case EARLIEST:
            case LATEST:
            case NON_STOPPING:
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("Unrecognized offset kind \"%s\"", offsetKind));
        }
    }
}
