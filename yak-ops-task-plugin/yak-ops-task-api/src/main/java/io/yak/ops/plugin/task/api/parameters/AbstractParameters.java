package io.yak.ops.plugin.task.api.parameters;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.enums.Direct;
import io.yak.ops.plugin.task.api.model.Property;
import io.yak.ops.plugin.task.api.model.ResourceInfo;
import io.yak.ops.plugin.task.api.parameters.resource.ResourceParametersHelper;
import io.yak.ops.plugin.task.api.utils.VarPoolUtils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public abstract class AbstractParameters implements IParameters {

    @Setter
    public List<Property> localParams;

    @Setter
    public List<Property> varPool = new ArrayList<>();

    @Override
    public abstract boolean checkParameters();

    @Override
    public List<ResourceInfo> getResourceFilesList() {
        return new ArrayList<>();
    }

    public void dealOutParam(Map<String, String> taskOutputParams) {
        List<Property> outProperty = getOutProperty(localParams);
        if (CollectionUtils.isEmpty(outProperty)) {
            return;
        }
        if (MapUtils.isNotEmpty(taskOutputParams)) {
            // Inject the value
            for (Property info : outProperty) {
                if (taskOutputParams.containsKey(info.getProp())) {
                    info.setValue(taskOutputParams.get(info.getProp()));
                }
            }
        }

        varPool = VarPoolUtils.mergeVarPool(Lists.newArrayList(varPool, outProperty));
    }

    protected List<Property> getOutProperty(List<Property> params) {
        if (CollectionUtils.isEmpty(params)) {
            return new ArrayList<>();
        }
        return params.stream()
                .filter(info -> info.getDirect() == Direct.OUT)
                .collect(Collectors.toList());
    }

    public List<Map<String, String>> getListMapByString(String json) {
        List<Map<String, String>> allParams = new ArrayList<>();
        ArrayNode paramsByJson = JSONUtils.parseArray(json);
        for (JsonNode jsonNode : paramsByJson) {
            Map<String, String> param = JSONUtils.toMap(jsonNode.toString());
            allParams.add(param);
        }
        return allParams;
    }

    public ResourceParametersHelper getResources() {
        return new ResourceParametersHelper();
    }

    public void addPropertyToValPool(Property property) {
        varPool.removeIf(p -> p.getProp().equals(property.getProp()));
        varPool.add(property);
    }
}
