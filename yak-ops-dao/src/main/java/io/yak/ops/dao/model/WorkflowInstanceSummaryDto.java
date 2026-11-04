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

package io.yak.ops.dao.model;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.TaskDependType;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceSummaryDto {

    private Integer id;

    private String name;

    private int workflowDefinitionVersion;

    private Long workflowDefinitionCode;

    private Long projectCode;

    private WorkflowExecutionStatus state;

    private Flag recovery;

    private Date startTime;

    private Date endTime;

    private int runTimes;

    private String host;

    private CommandType commandType;

    private TaskDependType taskDependType;

    private int maxTryTimes;

    private FailureStrategy failureStrategy;

    private WarningType warningType;

    private Integer warningGroupId;

    private Date scheduleTime;

    private Date commandStartTime;

    private Flag isSubWorkflow;

    private int executorId;

    private Priority workflowInstancePriority;

    private String workerGroup;

    private Long environmentCode;

    private int timeout;

    private String tenantCode;

    private int dryRun;

    private int nextWorkflowInstanceId;

    private Date restartTime;

    /**
     * Executor name, populated by the application layer (not a DB column in the listSql projection).
     */
    private String executorName;

    /**
     * Workflow execution duration, e.g. "1h 2m 3s", populated by the application layer.
     */
    private String duration;

}
