package io.yak.ops.dao.entity;

import lombok.Data;

/**
 * dependent node simplify definition
 */
@Data
public class DependentSimplifyDefinition {

    /**
     * definition code
     */
    private Long code;

    /**
     * definition name
     */
    private String name;

    /**
     * definition version
     */
    private Integer version;
}
