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

package io.yak.ops.server.master.engine.command.handler;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.server.master.config.MasterConfig;
import io.yak.ops.server.master.runner.WorkflowExecuteContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RecoverSerialWaitCommandHandler extends AbstractCommandHandler {

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Autowired
    private MasterConfig masterConfig;

    @Override
    protected void assembleWorkflowInstance(WorkflowExecuteContext.WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        final Command command = workflowExecuteContextBuilder.getCommand();
        final int workflowInstanceId = command.getWorkflowInstanceId();
        final WorkflowInstance workflowInstance = workflowInstanceDao.queryOptionalById(workflowInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Cannot find WorkflowInstance:" + workflowInstanceId));
        workflowInstance.setStateWithDesc(WorkflowExecutionStatus.RUNNING_EXECUTION, command.getCommandType().name());
        workflowInstance.setHost(masterConfig.getMasterAddress());
        workflowInstanceDao.updateById(workflowInstance);
        workflowExecuteContextBuilder.setWorkflowInstance(workflowInstance);
    }

    @Override
    protected void assembleWorkflowExecutionGraph(WorkflowExecuteContext.WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {

    }

    @Override
    public CommandType commandType() {
        return CommandType.RECOVER_SERIAL_WAIT;
    }
}
