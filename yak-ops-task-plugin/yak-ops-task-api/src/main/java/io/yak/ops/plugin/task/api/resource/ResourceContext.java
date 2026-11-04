package io.yak.ops.plugin.task.api.resource;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
public class ResourceContext {

    /**
     * ResourceAbsolutePathInStorage -> ResourceItem
     */
    private final Map<String, ResourceItem> resourceItemMap;

    public ResourceContext() {
        this.resourceItemMap = new HashMap<>();
    }

    public void addResourceItem(ResourceItem resourceItem) {
        checkNotNull(resourceItem);
        resourceItemMap.put(resourceItem.getResourceAbsolutePathInStorage(), resourceItem);
    }

    public ResourceItem getResourceItem(String resourceAbsolutePathInStorage) {
        ResourceItem resourceItem = resourceItemMap.get(resourceAbsolutePathInStorage);
        if (resourceItem == null) {
            throw new IllegalArgumentException("Cannot find the resourceItem: " + resourceAbsolutePathInStorage);
        }
        return resourceItem;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ResourceItem {

        private String resourceAbsolutePathInStorage;
        private String resourceAbsolutePathInLocal;
    }

}
