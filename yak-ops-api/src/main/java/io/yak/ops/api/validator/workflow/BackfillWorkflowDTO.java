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

package io.yak.ops.api.validator.workflow;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.ComplementDependentMode;
import io.yak.ops.common.enums.ExecutionOrder;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.RunMode;
import io.yak.ops.common.enums.TaskDependType;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.plugin.task.api.model.Property;

import java.time.ZonedDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackfillWorkflowDTO {

    private User loginUser;

    private WorkflowDefinition workflowDefinition;

    private List<Long> startNodes;

    private FailureStrategy failureStrategy;

    private TaskDependType taskDependType;

    private CommandType execType;

    private WarningType warningType;

    private Integer warningGroupId;

    private RunMode runMode;

    private Priority workflowInstancePriority;

    private String workerGroup;

    private String tenantCode;

    private Long environmentCode;

    private List<Property> startParamList;

    private Flag dryRun;

    private Long triggerCode;

    private BackfillParamsDTO backfillParams;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackfillParamsDTO {

        private RunMode runMode;

        private List<ZonedDateTime> backfillDateList;

        private Integer expectedParallelismNumber;

        private ComplementDependentMode backfillDependentMode;

        private boolean allLevelDependent;
        private ExecutionOrder executionOrder;
    }

}
