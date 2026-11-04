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

package io.yak.ops.server.master.engine.task.lifecycle.handler;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.client.ITaskExecutorClient;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskRunningLifecycleEvent;
import io.yak.ops.server.master.engine.task.statemachine.ITaskStateAction;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;
import io.yak.ops.task.executor.events.TaskExecutorLifecycleEventType;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TaskRunningLifecycleEventHandler extends AbstractTaskLifecycleEventHandler<TaskRunningLifecycleEvent> {

    private final ITaskExecutorClient taskExecutorClient;

    public TaskRunningLifecycleEventHandler(final ITaskExecutorClient taskExecutorClient) {
        this.taskExecutorClient = taskExecutorClient;
    }

    @Override
    public void handle(final ITaskStateAction taskStateAction,
                       final IWorkflowExecution workflowExecution,
                       final ITaskExecution taskExecution,
                       final TaskRunningLifecycleEvent taskRunningEvent) {
        taskStateAction.onStartedEvent(workflowExecution, taskExecution, taskRunningEvent);
        taskExecutorClient.ackTaskExecutorLifecycleEvent(
                taskExecution,
                new ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck(
                        taskExecution.getId(),
                        TaskExecutorLifecycleEventType.RUNNING));
    }

    @Override
    public ILifecycleEventType matchEventType() {
        return TaskLifecycleEventType.RUNNING;
    }

}
