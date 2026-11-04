package io.yak.ops.service.process;

import io.yak.ops.common.model.TaskNodeRelation;
import io.yak.ops.service.model.TaskNode;

import java.util.List;

import lombok.Data;

@Data
public class WorkflowDag {

    /**
     * DAG edge list
     **/
    private List<TaskNodeRelation> edges;

    /**
     * DAG node list
     */
    private List<TaskNode> nodes;

    @Override
    public String toString() {
        return "WorkflowDag{"
                + "edges=" + edges
                + ", nodes=" + nodes
                + '}';
    }
}
