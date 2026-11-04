package org.apache.flink.cdc.runtime.typeutils;

import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.data.RecordData;
import org.apache.flink.cdc.common.data.binary.BinaryRecordData;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.RowType;
import org.apache.flink.cdc.runtime.serializer.InternalSerializers;
import org.apache.flink.cdc.runtime.serializer.data.writer.BinaryRecordDataWriter;
import org.apache.flink.cdc.runtime.serializer.data.writer.BinaryWriter;

import java.util.Arrays;

import static org.apache.flink.cdc.common.utils.Preconditions.checkArgument;

/** This class is used to create {@link BinaryRecordData}. */
@PublicEvolving
public class BinaryRecordDataGenerator {

    private final DataType[] dataTypes;
    private final TypeSerializer[] serializers;

    private transient BinaryRecordData reuseRecordData;
    private transient BinaryRecordDataWriter reuseWriter;

    public BinaryRecordDataGenerator(RowType recordType) {
        this(recordType.getChildren().toArray(new DataType[0]));
    }

    public BinaryRecordDataGenerator(DataType[] dataTypes) {
        this(
                dataTypes,
                Arrays.stream(dataTypes)
                        .map(InternalSerializers::create)
                        .toArray(TypeSerializer[]::new));
    }

    public BinaryRecordDataGenerator(DataType[] dataTypes, TypeSerializer[] serializers) {
        checkArgument(
                dataTypes.length == serializers.length,
                "The types and serializers must have the same length. But types is %s and serializers is %s",
                dataTypes.length,
                serializers.length);

        this.dataTypes = dataTypes;
        this.serializers = serializers;

        this.reuseRecordData = new BinaryRecordData(dataTypes.length);
        this.reuseWriter = new BinaryRecordDataWriter(reuseRecordData);
    }

    /**
     * Creates an instance of {@link BinaryRecordData} with given field values.
     *
     * <p>Note: All fields of the record must be internal data structures. See {@link RecordData}.
     */
    public BinaryRecordData generate(Object[] rowFields) {
        checkArgument(
                dataTypes.length == rowFields.length,
                "The types and values must have the same length. But types is %s and values is %s",
                dataTypes.length,
                rowFields.length);

        reuseWriter.reset();
        for (int i = 0; i < dataTypes.length; i++) {
            if (rowFields[i] == null) {
                reuseWriter.setNullAt(i);
            } else {
                BinaryWriter.write(reuseWriter, i, rowFields[i], dataTypes[i], serializers[i]);
            }
        }
        reuseWriter.complete();
        return reuseRecordData.copy();
    }
}
