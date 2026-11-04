package io.yak.ops.server.master.engine.graph;

import io.yak.ops.dao.entity.TaskDefinition;

import java.util.List;
import java.util.Set;

public interface IWorkflowGraph {

    /**
     * Get the start nodes of the workflow graph.
     * <p> The start nodes are the tasks which has no predecessors.
     */
    List<String> getStartNodes();

    /**
     * Get the tasks which is the parent of given task.
     */
    Set<String> getPredecessors(String taskName);

    /**
     * Return the tasks which is post of given taskCode and should be triggered next.
     * <p> This method will not return the task which is forbiddenTask.
     */
    Set<String> getSuccessors(String taskName);

    /**
     * Get the task by task code.
     */
    TaskDefinition getTaskNodeByCode(Long taskCode);

    /**
     * Get the task by task name.
     */
    TaskDefinition getTaskNodeByName(String taskName);

    /**
     * Get all the task nodes in the workflow graph.
     */
    List<TaskDefinition> getAllTaskNodes();

}
