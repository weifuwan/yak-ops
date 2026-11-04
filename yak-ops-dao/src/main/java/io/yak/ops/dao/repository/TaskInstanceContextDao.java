package io.yak.ops.dao.repository;

import io.yak.ops.common.enums.ContextType;
import io.yak.ops.dao.entity.TaskInstanceContext;

import java.util.List;

public interface TaskInstanceContextDao extends IDao<TaskInstanceContext> {

    List<TaskInstanceContext> queryListByTaskInstanceIdAndContextType(Integer taskInstanceId,
                                                                      ContextType contextType);

    int deleteByTaskInstanceIdAndContextType(Integer taskInstanceId,
                                             ContextType contextType);

    int upsertTaskInstanceContext(TaskInstanceContext taskDependentResult);

    List<TaskInstanceContext> batchQueryByTaskInstanceIdsAndContextType(List<Integer> taskInstanceIds,
                                                                        ContextType contextType);
}
