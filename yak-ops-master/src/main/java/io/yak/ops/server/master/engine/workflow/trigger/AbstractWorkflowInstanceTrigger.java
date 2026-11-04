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

package io.yak.ops.server.master.engine.workflow.trigger;

import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.common.enums.WorkflowExecutionTypeEnum;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.WorkflowDefinitionLog;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.model.SerialCommandDto;
import io.yak.ops.dao.repository.CommandDao;
import io.yak.ops.dao.repository.SerialCommandDao;
import io.yak.ops.dao.repository.WorkflowDefinitionLogDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractWorkflowInstanceTrigger<TriggerRequest, TriggerResponse>
        implements
            IWorkflowTrigger<TriggerRequest, TriggerResponse> {

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Autowired
    private CommandDao commandDao;

    @Autowired
    protected SerialCommandDao serialCommandDao;

    @Autowired
    protected WorkflowDefinitionLogDao workflowDefinitionLogDao;

    @Override
    @Transactional
    public TriggerResponse triggerWorkflow(final TriggerRequest triggerRequest) {
        final WorkflowInstance workflowInstance = constructWorkflowInstance(triggerRequest);
        final Long workflowDefinitionCode = workflowInstance.getWorkflowDefinitionCode();
        final int workflowDefinitionVersion = workflowInstance.getWorkflowDefinitionVersion();
        final WorkflowDefinitionLog workflowDefinition = workflowDefinitionLogDao.queryByDefinitionCodeAndVersion(
                workflowDefinitionCode, workflowDefinitionVersion);
        if (workflowDefinition == null) {
            throw new IllegalStateException(
                    "Workflow definition not found: " + workflowDefinitionCode + " version: "
                            + workflowDefinitionVersion);
        }
        final Command command = constructTriggerCommand(triggerRequest, workflowInstance);
        if (workflowDefinition.getExecutionType() == WorkflowExecutionTypeEnum.PARALLEL) {
            workflowInstanceDao.updateById(workflowInstance);
            commandDao.insert(command);
        } else {
            workflowInstance.setState(WorkflowExecutionStatus.SERIAL_WAIT);
            workflowInstanceDao.updateById(workflowInstance);
            serialCommandDao.insert(SerialCommandDto.newSerialCommand(command).toEntity());
        }

        return onTriggerSuccess(workflowInstance);
    }

    // todo: 使用WorkflowInstanceConstructor封装
    protected abstract WorkflowInstance constructWorkflowInstance(final TriggerRequest triggerRequest);

    // todo: 使用CommandConstructor封装
    protected abstract Command constructTriggerCommand(final TriggerRequest triggerRequest,
                                                       final WorkflowInstance workflowInstance);

    protected abstract TriggerResponse onTriggerSuccess(final WorkflowInstance workflowInstance);

    protected WorkflowInstance getWorkflowInstance(final Integer workflowInstanceId) {
        final WorkflowInstance workflowInstance = workflowInstanceDao.queryById(workflowInstanceId);
        if (workflowInstance == null) {
            throw new IllegalStateException("Workflow instance not found: " + workflowInstanceId);
        }
        return workflowInstance;
    }

}
