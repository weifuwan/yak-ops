package io.yak.ops.api.dto.resources;

import io.yak.ops.spi.enums.ResourceType;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public abstract class ResourceComponent {

    protected String name;
    protected String currentDir;
    protected String fullName;
    protected String description;
    protected boolean isDirctory;

    /**
     * resoruce type
     */
    protected ResourceType type;
    protected List<ResourceComponent> children = new ArrayList<>();

    /**
     * add resource component
     *
     * @param resourceComponent resource component
     */
    public void add(ResourceComponent resourceComponent) {
        children.add(resourceComponent);
    }

}
