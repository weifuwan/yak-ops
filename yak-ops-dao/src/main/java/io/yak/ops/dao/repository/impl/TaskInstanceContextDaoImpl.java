package io.yak.ops.dao.repository.impl;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.ContextType;
import io.yak.ops.dao.entity.AbstractTaskInstanceContext;
import io.yak.ops.dao.entity.DependentResultTaskInstanceContext;
import io.yak.ops.dao.entity.TaskInstanceContext;
import io.yak.ops.dao.mapper.TaskInstanceContextMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.TaskInstanceContextDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

@Repository
public class TaskInstanceContextDaoImpl extends BaseDao<TaskInstanceContext, TaskInstanceContextMapper>
        implements
            TaskInstanceContextDao {

    public TaskInstanceContextDaoImpl(TaskInstanceContextMapper taskInstanceContextMapper) {
        super(taskInstanceContextMapper);
    }

    @Override
    public List<TaskInstanceContext> queryListByTaskInstanceIdAndContextType(Integer taskInstanceId,
                                                                             ContextType contextType) {
        if (taskInstanceId == null) {
            return Collections.emptyList();
        }
        return mybatisMapper.queryListByTaskInstanceIdAndContextType(taskInstanceId, contextType);
    }

    @Override
    public int deleteByTaskInstanceIdAndContextType(Integer taskInstanceId, ContextType contextType) {
        if (taskInstanceId == null) {
            throw new IllegalArgumentException("taskInstanceId cannot be null");
        }
        return mybatisMapper.deleteByTaskInstanceIdAndContextType(taskInstanceId, contextType);
    }

    @Override
    public int upsertTaskInstanceContext(TaskInstanceContext taskInstanceContext) {
        if (taskInstanceContext == null) {
            return 0;
        }
        TaskInstanceContext dbTaskInstanceContext =
                mybatisMapper.queryListByTaskInstanceIdAndContextType(taskInstanceContext.getTaskInstanceId(),
                        taskInstanceContext.getContextType()).stream().findFirst().orElse(null);
        if (dbTaskInstanceContext == null) {
            return mybatisMapper.insert(taskInstanceContext);
        } else {
            List<AbstractTaskInstanceContext> dbDependentResultTaskInstanceContextList =
                    dbTaskInstanceContext.getTaskInstanceContext();
            dbDependentResultTaskInstanceContextList.addAll(taskInstanceContext.getTaskInstanceContext());
            List<AbstractTaskInstanceContext> deduplicatedDependentResultTaskInstanceContextList =
                    dbDependentResultTaskInstanceContextList.stream()
                            .map(DependentResultTaskInstanceContext.class::cast)
                            .collect(Collectors.collectingAndThen(
                                    Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(
                                            o -> o.getProjectCode() + Constants.UNDERLINE
                                                    + o.getWorkflowDefinitionCode() + Constants.UNDERLINE
                                                    + o.getTaskDefinitionCode() + Constants.UNDERLINE
                                                    + o.getDateCycle()))),
                                    ArrayList::new));
            taskInstanceContext.setTaskInstanceContext(deduplicatedDependentResultTaskInstanceContextList);
            return mybatisMapper.updateById(taskInstanceContext);
        }
    }

    @Override
    public List<TaskInstanceContext> batchQueryByTaskInstanceIdsAndContextType(List<Integer> taskInstanceIds,
                                                                               ContextType contextType) {
        if (CollectionUtils.isEmpty(taskInstanceIds)) {
            return Collections.emptyList();
        }
        return mybatisMapper.batchQueryByTaskInstanceIdsAndContextType(taskInstanceIds, contextType);
    }
}
