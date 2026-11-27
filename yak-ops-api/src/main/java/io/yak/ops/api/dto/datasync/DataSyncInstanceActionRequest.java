package io.yak.ops.api.dto.datasync;

import io.yak.ops.api.enums.DataSyncInstanceAction;

import lombok.Data;

@Data
public class DataSyncInstanceActionRequest {

    private DataSyncInstanceAction action;
}
