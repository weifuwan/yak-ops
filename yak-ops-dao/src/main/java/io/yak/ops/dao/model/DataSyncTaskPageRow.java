package io.yak.ops.dao.model;

import io.yak.ops.dao.entity.TaskDefinition;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataSyncTaskPageRow extends TaskDefinition {

    private long workflowCode;
    private int workflowReleaseState;
}
