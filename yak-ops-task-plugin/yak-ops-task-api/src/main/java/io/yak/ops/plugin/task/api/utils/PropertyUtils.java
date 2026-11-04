package io.yak.ops.plugin.task.api.utils;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.enums.DataType;
import io.yak.ops.plugin.task.api.enums.Direct;
import io.yak.ops.plugin.task.api.model.Property;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PropertyUtils {

    private PropertyUtils() {
        throw new IllegalStateException("PropertyUtils class");
    }

    /**
     * startParams transform propertyList
     *
     * @param startParams startParams
     * @return startParamList
     */
    public static List<Property> startParamsTransformPropertyList(String startParams) {
        List<Property> startParamList = null;
        if (startParams != null) {
            try {
                Map<String, String> startParamMap = JSONUtils.toMap(startParams);
                startParamList = startParamMap.entrySet().stream()
                        .map(entry -> new Property(entry.getKey(), Direct.IN, DataType.VARCHAR, entry.getValue()))
                        .collect(Collectors.toList());
            } catch (Exception ignore) {
                startParamList = JSONUtils.toList(startParams, Property.class);
            }
        }
        return startParamList;
    }

}
