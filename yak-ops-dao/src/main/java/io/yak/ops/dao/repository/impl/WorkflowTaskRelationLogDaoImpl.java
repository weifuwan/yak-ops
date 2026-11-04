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

import io.yak.ops.dao.entity.WorkflowTaskRelationLog;
import io.yak.ops.dao.mapper.WorkflowTaskRelationLogMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.WorkflowTaskRelationLogDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class WorkflowTaskRelationLogDaoImpl extends BaseDao<WorkflowTaskRelationLog, WorkflowTaskRelationLogMapper>
        implements
            WorkflowTaskRelationLogDao {

    public WorkflowTaskRelationLogDaoImpl(@NonNull WorkflowTaskRelationLogMapper workflowTaskRelationLogMapper) {
        super(workflowTaskRelationLogMapper);
    }

    @Override
    public List<WorkflowTaskRelationLog> queryByWorkflowDefinitionCode(long workflowDefinitionCode) {
        return mybatisMapper.queryByWorkflowDefinitionCode(workflowDefinitionCode);
    }

    @Override
    public void deleteByWorkflowDefinitionCode(long workflowDefinitionCode) {
        mybatisMapper.deleteByWorkflowDefinitionCode(workflowDefinitionCode);
    }

    @Override
    public int batchInsert(List<WorkflowTaskRelationLog> taskRelationList) {
        if (CollectionUtils.isEmpty(taskRelationList)) {
            return 0;
        }
        return mybatisMapper.batchInsert(taskRelationList);
    }
}
