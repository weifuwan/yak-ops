package org.apache.flink.cdc.common.schema;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.types.DataType;

import javax.annotation.Nullable;

import java.util.Objects;

/** Representation of a metadata column. */
@PublicEvolving
public class MetadataColumn extends Column {

    private static final long serialVersionUID = 1L;

    private final @Nullable String metadataKey;

    public MetadataColumn(
            String name, DataType type, @Nullable String metadataKey, @Nullable String comment) {
        super(name, type, comment);
        this.metadataKey = metadataKey;
    }

    @Override
    public boolean isPhysical() {
        return false;
    }

    @Nullable
    public String getMetadataKey() {
        return metadataKey;
    }

    @Override
    public Column copy(DataType newType) {
        return new MetadataColumn(name, newType, metadataKey, comment);
    }

    @Override
    public Column copy(String newName) {
        return new MetadataColumn(newName, type, metadataKey, comment);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MetadataColumn)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        MetadataColumn that = (MetadataColumn) o;
        return Objects.equals(metadataKey, that.metadataKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), metadataKey);
    }
}
