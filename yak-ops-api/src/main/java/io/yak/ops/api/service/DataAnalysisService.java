package io.yak.ops.api.service;

import io.yak.ops.api.dto.CommandStateCount;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.vo.TaskInstanceCountVO;
import io.yak.ops.api.vo.WorkflowDefinitionCountVO;
import io.yak.ops.api.vo.WorkflowInstanceCountVO;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.ErrorCommand;
import io.yak.ops.dao.entity.User;

import java.util.List;
import java.util.Map;

public interface DataAnalysisService {

    TaskInstanceCountVO getTaskInstanceStateCountByProject(User loginUser,
                                                           Long projectCode,
                                                           String startDate,
                                                           String endDate);

    TaskInstanceCountVO getAllTaskInstanceStateCount(User loginUser,
                                                     String startDate,
                                                     String endDate);

    WorkflowInstanceCountVO getWorkflowInstanceStateCountByProject(User loginUser,
                                                                   Long projectCodes,
                                                                   String startDate,
                                                                   String endDate);

    WorkflowInstanceCountVO getAllWorkflowInstanceStateCount(User loginUser,
                                                             String startDate,
                                                             String endDate);

    WorkflowDefinitionCountVO getWorkflowDefinitionCountByProject(User loginUser, Long projectCode);

    WorkflowDefinitionCountVO getAllWorkflowDefinitionCount(User loginUser);

    /**
     * statistical command status data
     *
     * @param loginUser login user
     * @return command state count data
     */
    List<CommandStateCount> countCommandState(User loginUser);

    /**
     * count queue state
     *
     * @param loginUser login user
     * @return queue state count data
     */
    Map<String, Integer> countQueueState(User loginUser);

    PageInfo<Command> listPendingCommands(User loginUser, Long projectCode, Integer pageNo, Integer pageSize);

    PageInfo<ErrorCommand> listErrorCommand(User loginUser, Long projectCode, Integer pageNo, Integer pageSize);
}
