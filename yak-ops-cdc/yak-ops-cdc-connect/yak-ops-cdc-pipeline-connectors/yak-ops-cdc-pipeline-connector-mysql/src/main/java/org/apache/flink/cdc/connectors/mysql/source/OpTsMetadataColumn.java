package org.apache.flink.cdc.connectors.mysql.source;

import org.apache.flink.cdc.common.source.SupportedMetadataColumn;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.DataTypes;

import java.util.Map;

/** A {@link SupportedMetadataColumn} for op_ts. */
public class OpTsMetadataColumn implements SupportedMetadataColumn {

    @Override
    public String getName() {
        return "op_ts";
    }

    @Override
    public DataType getType() {
        return DataTypes.BIGINT().notNull();
    }

    @Override
    public Class<?> getJavaClass() {
        return Long.class;
    }

    @Override
    public Object read(Map<String, String> metadata) {
        if (metadata.containsKey(getName())) {
            return Long.parseLong(metadata.get(getName()));
        }
        throw new IllegalArgumentException("op_ts doesn't exist in the metadata: " + metadata);
    }
}
