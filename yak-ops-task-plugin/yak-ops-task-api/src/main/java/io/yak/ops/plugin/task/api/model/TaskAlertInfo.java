package io.yak.ops.plugin.task.api.model;

import lombok.Data;

@Data
public class TaskAlertInfo {

    private String title;

    private String content;

    private Integer alertGroupId;
}
