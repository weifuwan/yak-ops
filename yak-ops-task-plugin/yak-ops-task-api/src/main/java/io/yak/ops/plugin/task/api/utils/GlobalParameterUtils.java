package io.yak.ops.plugin.task.api.utils;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.model.Property;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class GlobalParameterUtils {

    public String serializeGlobalParameter(List<Property> globalParams) {
        if (CollectionUtils.isEmpty(globalParams)) {
            return null;
        }
        return JSONUtils.toJsonString(globalParams);
    }

    public List<Property> deserializeGlobalParameter(String globalParams) {
        List<Property> result = JSONUtils.parseObject(globalParams, new TypeReference<List<Property>>() {
        });
        return result == null ? Collections.emptyList() : result;
    }

}
