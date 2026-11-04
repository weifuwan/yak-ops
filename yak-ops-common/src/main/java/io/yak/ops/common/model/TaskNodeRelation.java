package io.yak.ops.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskNodeRelation {

    /**
     * task start node Code
     */
    private Long startNode;

    /**
     * task end node Code
     */
    private Long endNode;

}
