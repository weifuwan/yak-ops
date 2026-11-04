package org.apache.flink.cdc.connectors.mysql.source.offset;

import org.apache.flink.cdc.common.annotation.Internal;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/** Serializer implementation for a {@link BinlogOffset}. */
@Internal
public class BinlogOffsetSerializer {

    public static final BinlogOffsetSerializer INSTANCE = new BinlogOffsetSerializer();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public byte[] serialize(BinlogOffset binlogOffset) throws IOException {
        // use JSON serialization
        return OBJECT_MAPPER.writeValueAsBytes(binlogOffset.getOffset());
    }

    public BinlogOffset deserialize(byte[] bytes) throws IOException {
        Map<String, String> offset = OBJECT_MAPPER.readValue(bytes, Map.class);
        return new BinlogOffset(offset);
    }
}
