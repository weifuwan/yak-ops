package io.yak.ops.dao.entity;

import lombok.Builder;
import lombok.Data;

/**
 * User and task flow binding relationship
 */
@Data
@Builder
public class UserWithWorkflowDefinitionCode {

    private long workflowDefinitionCode;

    private int workflowDefinitionVersion;

    private Integer modifierId;

    private String modifierName;

    private Integer creatorId;

    private String creatorName;

}
