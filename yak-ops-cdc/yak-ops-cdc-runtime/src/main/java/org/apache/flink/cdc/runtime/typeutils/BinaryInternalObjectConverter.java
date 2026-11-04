package org.apache.flink.cdc.runtime.typeutils;

import org.apache.flink.cdc.common.converter.InternalObjectConverter;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.RowType;
import org.apache.flink.cdc.common.utils.ThreadLocalCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Also an {@link InternalObjectConverter} but encodes {@link
 * org.apache.flink.cdc.common.data.RecordData} as {@link
 * org.apache.flink.cdc.common.data.binary.BinaryRecordData}.
 */
public class BinaryInternalObjectConverter extends InternalObjectConverter {

    private static final ThreadLocalCache<RowType, BinaryRecordDataGenerator> GENERATOR_CACHE =
            ThreadLocalCache.of(BinaryRecordDataGenerator::new);

    public static Object convertToInternal(Object obj, DataType dataType) {
        if (obj == null) {
            return null;
        }
        if (dataType instanceof RowType) {
            RowType rowType = (RowType) dataType;
            if (!(obj instanceof List<?>)) {
                throw new IllegalArgumentException(
                        "Expected RowType "
                                + rowType
                                + " converter object to be List<?>, but got "
                                + obj.getClass());
            }
            List<DataType> dataTypes = rowType.getFieldTypes();
            List<?> javaObjects = (List<?>) obj;
            if (dataTypes.size() != javaObjects.size()) {
                throw new IllegalArgumentException(
                        String.format(
                                "RowType %s arity (%s) and object count (%s) mismatch.",
                                dataType, dataTypes.size(), javaObjects.size()));
            }
            BinaryRecordDataGenerator generator = GENERATOR_CACHE.get(rowType);
            List<Object> convertedInternalObjects = new ArrayList<>(rowType.getFieldCount());
            for (int i = 0; i < rowType.getFieldCount(); i++) {
                convertedInternalObjects.add(
                        convertToInternal(javaObjects.get(i), dataTypes.get(i)));
            }
            return generator.generate(convertedInternalObjects.toArray());
        }
        return InternalObjectConverter.convertToInternal(obj, dataType);
    }
}
