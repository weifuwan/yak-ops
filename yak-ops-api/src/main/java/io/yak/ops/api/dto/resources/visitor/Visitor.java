package io.yak.ops.api.dto.resources.visitor;

import io.yak.ops.api.dto.resources.ResourceComponent;

public interface Visitor {

    /**
     * visit
     * @return resource component
     */
    ResourceComponent visit(String rootPath);
}
