package io.yak.ops.server.master.engine.workflow.serial;

import io.yak.ops.common.enums.WorkflowExecutionTypeEnum;
import io.yak.ops.dao.model.SerialCommandDto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SerialCommandsGroup {

    private Long workflowDefinitionCode;
    private Integer workflowDefinitionVersion;
    private WorkflowExecutionTypeEnum executionType;
    private List<SerialCommandDto> serialCommands;

}
