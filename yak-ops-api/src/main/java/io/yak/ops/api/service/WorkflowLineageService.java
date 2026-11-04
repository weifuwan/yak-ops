package io.yak.ops.api.service;

import io.yak.ops.dao.entity.DependentLineageTask;
import io.yak.ops.dao.entity.DependentWorkflowDefinition;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkFlowLineage;
import io.yak.ops.dao.entity.WorkFlowRelationDetail;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowTaskLineage;

import java.util.List;
import java.util.Optional;

public interface WorkflowLineageService {

    List<WorkFlowRelationDetail> queryWorkFlowLineageByName(User loginUser, long projectCode,
                                                            String workflowDefinitionName);

    WorkFlowLineage queryWorkFlowLineageByCode(User loginUser, long projectCode, long workflowDefinitionCode);

    WorkFlowLineage queryWorkFlowLineage(User loginUser, long projectCode);

    /**
     * Query downstream tasks depend on a workflow definition or a task
     *
     * @param workflowDefinitionCode workflow definition code want to query tasks dependence
     * @return downstream dependent workflow definition list
     */
    List<DependentWorkflowDefinition> queryDownstreamDependentWorkflowDefinitions(Long workflowDefinitionCode);

    /**
     * Resolve downstream workflow definitions that depend on the given root workflow, using stored lineage.
     * <ul>
     *     <li>{@code allLevelDependent == false}: only direct dependents of the root.</li>
     *     <li>{@code allLevelDependent == true}: transitive dependents (BFS over direct dependents), skipping the root
     *     when it reappears as an edge target (cycle back to root).</li>
     * </ul>
     * When {@code filterOfflineWorkflow} is true, offline workflow definitions are excluded from the result and are
     * not expanded further during transitive traversal.
     *
     * @param rootWorkflowDefinitionCode workflow to start from (not included in the result)
     * @param allLevelDependent          {@code true} for transitive closure, {@code false} for one hop only
     * @param filterOfflineWorkflow      whether offline workflows should be filtered out during traversal
     * @return ordered distinct downstream workflow definitions (stable order: BFS / insertion order)
     */
    List<WorkflowDefinition> resolveDownstreamWorkflowDefinitionCodes(long rootWorkflowDefinitionCode,
                                                                      boolean allLevelDependent,
                                                                      boolean filterOfflineWorkflow);

    /**
     * Query and return tasks dependence with string format, is a wrapper of queryTaskDepOnTask and task query method.
     *
     * @param projectCode           Project code want to query tasks dependence
     * @param workflowDefinitionCode workflow definition code want to query tasks dependence
     * @param taskCode              Task code want to query tasks dependence
     * @return dependent workflow definition
     */
    Optional<String> taskDependentMsg(User loginUser, long projectCode, long workflowDefinitionCode, long taskCode);

    List<DependentLineageTask> queryDependentWorkflowDefinitions(User loginUser, long projectCode,
                                                                 long workflowDefinitionCode,
                                                                 Long taskCode);

    /**
     * Replace the lineage of given workflow definition by new lineage list.
     * When the list is empty, existing lineage data will be deleted.
     *
     * @param workflowDefinitionCode workflow definition to update
     * @param workflowTaskLineages   new lineage list, can be empty
     * @return affected rows
     */
    int updateWorkflowLineage(long workflowDefinitionCode, List<WorkflowTaskLineage> workflowTaskLineages);

    int deleteWorkflowLineage(List<Long> workflowDefinitionCodes);
}
