package io.yak.ops.plugin.task.api.parameters.resource;

import io.yak.ops.plugin.task.api.enums.ResourceType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ResourceParametersHelper {

    private Map<ResourceType, Map<Integer, AbstractResourceParameters>> resourceMap = new HashMap<>();

    public void put(ResourceType resourceType, Integer id) {
        put(resourceType, id, null);
    }

    public void put(ResourceType resourceType, Integer id, AbstractResourceParameters parameters) {
        Map<Integer, AbstractResourceParameters> resourceParametersMap = resourceMap.get(resourceType);
        if (Objects.isNull(resourceParametersMap)) {
            resourceParametersMap = new HashMap<>();
            resourceMap.put(resourceType, resourceParametersMap);
        }
        resourceParametersMap.put(id, parameters);
    }

    public void setResourceMap(Map<ResourceType, Map<Integer, AbstractResourceParameters>> resourceMap) {
        this.resourceMap = resourceMap;
    }

    public Map<ResourceType, Map<Integer, AbstractResourceParameters>> getResourceMap() {
        return resourceMap;
    }

    public Map<Integer, AbstractResourceParameters> getResourceMap(ResourceType resourceType) {
        return this.getResourceMap().get(resourceType);
    }

    public AbstractResourceParameters getResourceParameters(ResourceType resourceType, Integer code) {
        return this.getResourceMap(resourceType).get(code);
    }
}
