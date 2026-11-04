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

package io.yak.ops.server.master.engine.executor.plugin.condition;

import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.task.ConditionsLogicTaskChannelFactory;
import io.yak.ops.server.master.engine.IWorkflowRepository;
import io.yak.ops.server.master.engine.executor.plugin.ILogicTaskPluginFactory;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.task.executor.ITaskExecutor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ConditionLogicTaskPluginFactory implements ILogicTaskPluginFactory<ConditionLogicTask> {

    @Autowired
    private TaskInstanceDao taskInstanceDao;

    @Autowired
    private IWorkflowRepository workflowExecutionMemoryRepository;

    @Override
    public ConditionLogicTask createLogicTask(final ITaskExecutor taskExecutor) {
        final TaskExecutionContext taskExecutionContext = taskExecutor.getTaskExecutionContext();
        final IWorkflowExecution workflowExecution =
                workflowExecutionMemoryRepository.get(taskExecutionContext.getWorkflowInstanceId());
        return new ConditionLogicTask(workflowExecution, taskExecutionContext, taskInstanceDao);
    }

    @Override
    public String getTaskType() {
        return ConditionsLogicTaskChannelFactory.NAME;
    }
}
