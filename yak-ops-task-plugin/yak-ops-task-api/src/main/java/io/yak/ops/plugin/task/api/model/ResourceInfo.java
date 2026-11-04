package io.yak.ops.plugin.task.api.model;

import lombok.Data;

@Data
public class ResourceInfo {

    /**
     * res the name of the resource that was uploaded
     */
    @Deprecated
    private Integer id;

    /**
     * ResourceAbsolutePathInStorage
     */
    private String resourceName;

    @Deprecated
    private String res;
}
