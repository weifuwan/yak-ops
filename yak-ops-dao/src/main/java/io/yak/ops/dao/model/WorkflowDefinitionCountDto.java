package io.yak.ops.dao.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionCountDto {

    private String userName;

    private Integer userId;

    private int count;
}
