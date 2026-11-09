package io.yak.ops.plugin.task.api.model;

import io.yak.ops.common.enums.AlertType;

import lombok.Data;

@Data
public class TaskAlertInfo {

    private String title;

    private String content;

    private Integer alertGroupId;

    private AlertType alertType;
}
