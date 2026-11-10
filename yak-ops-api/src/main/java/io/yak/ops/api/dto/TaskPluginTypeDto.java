package io.yak.ops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TaskPluginTypeDto {

    private String taskType;

    private String taskCategory;
}
