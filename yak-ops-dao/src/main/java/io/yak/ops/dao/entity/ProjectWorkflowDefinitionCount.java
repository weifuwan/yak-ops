package io.yak.ops.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectWorkflowDefinitionCount {

    private Long projectCode;

    private Integer count;
}
