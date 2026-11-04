/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskDefinitionLog;
import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.mapper.TaskDefinitionLogMapper;
import io.yak.ops.dao.mapper.WorkflowTaskRelationLogMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.TaskDefinitionLogDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import lombok.NonNull;

/**
 * Task Definition Log DAP implementation
 */
@Repository
public class TaskDefinitionLogDaoImpl extends BaseDao<TaskDefinitionLog, TaskDefinitionLogMapper>
        implements
            TaskDefinitionLogDao {

    @Autowired
    private WorkflowTaskRelationLogMapper workflowTaskRelationLogMapper;

    public TaskDefinitionLogDaoImpl(@NonNull TaskDefinitionLogMapper taskDefinitionLogMapper) {
        super(taskDefinitionLogMapper);
    }

    @Override
    public List<TaskDefinitionLog> queryByWorkflowDefinitionCodeAndVersion(Long workflowDefinitionCode,
                                                                           Integer workflowDefinitionVersion) {

        List<WorkflowTaskRelation> workflowTaskRelationLogs = workflowTaskRelationLogMapper
                .queryByWorkflowCodeAndVersion(workflowDefinitionCode, workflowDefinitionVersion)
                .stream()
                .map(p -> (WorkflowTaskRelation) p)
                .collect(Collectors.toList());
        return queryTaskDefineLogList(workflowTaskRelationLogs);
    }

    @Override
    public List<TaskDefinitionLog> queryTaskDefineLogList(List<WorkflowTaskRelation> workflowTaskRelations) {
        if (CollectionUtils.isEmpty(workflowTaskRelations)) {
            return Collections.emptyList();
        }
        Set<TaskDefinition> taskDefinitionSet = workflowTaskRelations.stream()
                .filter(p -> p.getPostTaskCode() > 0)
                .map(p -> new TaskDefinition(p.getPostTaskCode(), p.getPostTaskVersion()))
                .collect(Collectors.toSet());

        if (CollectionUtils.isEmpty(taskDefinitionSet)) {
            return Collections.emptyList();
        }
        return mybatisMapper.queryByTaskDefinitions(taskDefinitionSet);
    }

    @Override
    public void deleteByTaskDefinitionCodes(Set<Long> taskDefinitionCodes) {
        if (CollectionUtils.isEmpty(taskDefinitionCodes)) {
            return;
        }
        mybatisMapper.deleteByTaskDefinitionCodes(taskDefinitionCodes);
    }
}
