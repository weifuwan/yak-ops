package org.apache.flink.cdc.runtime.serializer.data.util;

import org.apache.flink.cdc.common.data.ArrayData;
import org.apache.flink.cdc.common.data.MapData;
import org.apache.flink.cdc.common.types.DataType;

import java.util.HashMap;
import java.util.Map;

/** Utilities for {@link MapData}. */
public final class MapDataUtil {

    /**
     * Converts a {@link MapData} into Java {@link Map}, the keys and values of the Java map still
     * holds objects of internal data structures.
     */
    public static Map<Object, Object> convertToJavaMap(
            MapData map, DataType keyType, DataType valueType) {
        ArrayData keyArray = map.keyArray();
        ArrayData valueArray = map.valueArray();
        Map<Object, Object> javaMap = new HashMap<>();
        ArrayData.ElementGetter keyGetter = ArrayData.createElementGetter(keyType);
        ArrayData.ElementGetter valueGetter = ArrayData.createElementGetter(valueType);
        for (int i = 0; i < map.size(); i++) {
            Object key = keyGetter.getElementOrNull(keyArray, i);
            Object value = valueGetter.getElementOrNull(valueArray, i);
            javaMap.put(key, value);
        }
        return javaMap;
    }
}
