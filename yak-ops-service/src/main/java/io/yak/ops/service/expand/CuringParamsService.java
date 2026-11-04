package io.yak.ops.service.expand;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.plugin.task.api.model.Property;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

import java.util.Date;
import java.util.List;
import java.util.Map;

import lombok.NonNull;

public interface CuringParamsService {

    /**
     * convert parameter placeholders
     * @param val
     * @param allParamMap
     * @return
     */
    String convertParameterPlaceholders(String val, Map<String, Property> allParamMap);

    /**
     * curing global params
     * @param workflowInstanceId
     * @param globalParamMap
     * @param globalParamList
     * @param commandType
     * @param scheduleTime
     * @param timezone
     * @return
     */
    String curingGlobalParams(Integer workflowInstanceId, Map<String, String> globalParamMap,
                              List<Property> globalParamList, CommandType commandType, Date scheduleTime,
                              String timezone);

    /**
     * param parsing preparation
     * @param parameters
     * @param taskInstance
     * @param workflowInstance
     * @param projectName
     * @param workflowDefinitionName
     * @return
     */
    Map<String, Property> paramParsingPreparation(@NonNull TaskInstance taskInstance,
                                                  @NonNull AbstractParameters parameters,
                                                  @NonNull WorkflowInstance workflowInstance,
                                                  String projectName,
                                                  String workflowDefinitionName);

    /**
     * preBuildBusinessParams
     * @param workflowInstance
     * @return
     */
    Map<String, Property> preBuildBusinessParams(WorkflowInstance workflowInstance);

    Map<String, Property> getProjectParameterMap(long projectCode);
}
