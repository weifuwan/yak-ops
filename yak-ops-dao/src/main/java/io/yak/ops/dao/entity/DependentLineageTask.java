package io.yak.ops.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DependentLineageTask {

    private long projectCode;
    private long workflowDefinitionCode;
    private String workflowDefinitionName;
    private long taskDefinitionCode;
    private String taskDefinitionName;
}
